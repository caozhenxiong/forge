package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmToolCall;
import devflow.agent.executor.shell.ShellCommandDecision;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BashTool implements ImplementationTool {

    private static final ImplementationToolSpecification SPECIFICATION = new ImplementationToolSpecification(
            "Bash",
            "Run a one-shot shell command inside the current project root. Read-only commands are allowed; write-capable commands must stay inside current writable files. Background jobs started by the command are terminated when the call finishes.",
            Map.of(
                    "type", "object",
                    "required", List.of("command"),
                    "properties", Map.of(
                            "command", Map.of("type", "string"),
                            "timeout", Map.of("type", "integer")
                    )
            ),
            false,
            false,
            100_000,
            ImplementationToolPermissionScope.EXECUTE_SHELL
    );

    @Override
    public ImplementationToolSpecification specification() {
        return SPECIFICATION;
    }

    @Override
    public ToolInvocationResult invoke(LlmToolCall toolCall, ToolExecutionContext context) {
        try {
            Input input = context.objectMapper().convertValue(toolCall.arguments(), Input.class);
            String command = requireCommand(input.command());
            ShellCommandDecision decision = context.decideShellCommand(command);
            if (!decision.allowsExecution()) {
                context.appendEvent("实现阶段｜shell｜拒绝｜原因=%s｜可重试=%s｜命令=%s｜证据=%s"
                        .formatted(
                                decision.reasonCode(),
                                decision.retryable(),
                                decision.commandSummary(),
                                decision.evidence()
                ));
                return ToolInvocationResult.failure(renderDeniedPayload(decision));
            }
            long timeoutMs;
            try {
                timeoutMs = context.resolveShellTimeout(input.timeout());
                context.assertShellWriteTargets(decision.pathIntents());
            } catch (IllegalArgumentException validationException) {
                context.appendEvent("实现阶段｜shell｜执行前校验失败｜命令=%s｜原因=%s"
                        .formatted(decision.commandSummary(), validationException.getMessage()));
                return ToolInvocationResult.failure(renderPreExecutionDeniedPayload(decision, validationException.getMessage()));
            }
            context.appendEvent("实现阶段｜shell｜批准｜模式=%s｜命令=%s｜声明路径=%s"
                    .formatted(
                            decision.readOnly() ? "READ_ONLY" : "WRITE",
                            decision.commandSummary(),
                            decision.declaredWritePaths()
                    ));

            ToolExecutionContext.ShellWorkspaceSnapshot beforeSnapshot = context.captureShellWorkspaceSnapshot();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Process process = new ProcessBuilder("setsid", "bash", "-lc", command)
                    .directory(context.projectPath().toFile())
                    .start();
            long processGroupId = process.pid();
            Thread stdoutPump = startPump(process.getInputStream(), stdout);
            Thread stderrPump = startPump(process.getErrorStream(), stderr);
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            int exitCode;
            if (!finished) {
                destroyProcessGroup(processGroupId);
                process.waitFor(1, TimeUnit.SECONDS);
                exitCode = process.isAlive() ? 124 : process.exitValue();
            } else {
                exitCode = process.exitValue();
                destroyProcessGroup(processGroupId);
            }
            stdoutPump.join(1_000L);
            stderrPump.join(1_000L);
            ToolExecutionContext.ShellMutationAccountingResult mutationAccounting;
            try {
                mutationAccounting = context.recordShellWorkspaceChanges(
                        beforeSnapshot,
                        decision.readOnly()
                );
            } catch (IllegalArgumentException validationException) {
                context.appendEvent("实现阶段｜shell｜后置校验失败｜命令=%s｜原因=%s"
                        .formatted(decision.commandSummary(), validationException.getMessage()));
                return ToolInvocationResult.failure(renderPostExecutionValidationPayload(
                        decision,
                        exitCode,
                        stdout.toString(),
                        stderr.toString(),
                        timeoutMs,
                        !finished,
                        validationException.getMessage()
                ));
            }
            if (!mutationAccounting.scopeViolationPaths().isEmpty()) {
                context.appendEvent("实现阶段｜shell｜越界写入｜命令=%s｜越界路径=%s"
                        .formatted(decision.commandSummary(), mutationAccounting.scopeViolationPaths()));
                throw fatalScopeViolation(decision, exitCode, stdout.toString(), stderr.toString(), timeoutMs, !finished, mutationAccounting);
            }
            context.appendEvent("实现阶段｜shell｜完成｜命令=%s｜exitCode=%d｜timedOut=%s｜变更=%s"
                    .formatted(
                            decision.commandSummary(),
                            exitCode,
                            !finished,
                            mutationAccounting.changedPaths()
                    ));
            return ToolInvocationResult.success(renderSuccessPayload(
                    decision,
                    exitCode,
                    stdout.toString(),
                    stderr.toString(),
                    timeoutMs,
                    !finished,
                    mutationAccounting
            ));
        } catch (FatalToolExecutionException exception) {
            // fatal tool failure 必须直接上抛给 tool loop，由上层终止当前子任务并进入恢复链。
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ToolInvocationResult.failure(Map.of(
                    "type", "error",
                    "message", exception.getMessage() == null ? "Shell command was interrupted." : exception.getMessage()
            ));
        } catch (IllegalArgumentException exception) {
            return ToolInvocationResult.failure(Map.of("type", "error", "message", exception.getMessage()));
        } catch (Exception exception) {
            return ToolInvocationResult.failure(Map.of("type", "error", "message", exception.getMessage()));
        }
    }

    private record Input(
            String command,
            Long timeout
    ) {
    }

    private String requireCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command is required.");
        }
        return command;
    }

    private Map<String, Object> renderDeniedPayload(ShellCommandDecision decision) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", "SHELL_COMMAND_DENIED");
        payload.put("reasonCode", decision.reasonCode());
        payload.put("message", decision.message());
        payload.put("retryable", decision.retryable());
        payload.put("executed", false);
        payload.put("readOnly", decision.readOnly());
        payload.put("commandSummary", decision.commandSummary());
        payload.put("evidence", decision.evidence());
        payload.put("declaredWritePaths", renderPaths(decision.declaredWritePaths()));
        return payload;
    }

    private Map<String, Object> renderPreExecutionDeniedPayload(
            ShellCommandDecision decision,
            String message
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", "SHELL_COMMAND_DENIED");
        payload.put("reasonCode", "PRE_EXEC_VALIDATION_FAILED");
        payload.put("message", message == null ? "Shell command failed pre-execution validation." : message);
        payload.put("retryable", true);
        payload.put("executed", false);
        payload.put("readOnly", decision.readOnly());
        payload.put("commandSummary", decision.commandSummary());
        payload.put("evidence", decision.evidence());
        payload.put("declaredWritePaths", renderPaths(decision.declaredWritePaths()));
        return payload;
    }

    private Map<String, Object> renderScopeViolationPayload(
            ShellCommandDecision decision,
            int exitCode,
            String stdout,
            String stderr,
            long timeoutMs,
            boolean timedOut,
            ToolExecutionContext.ShellMutationAccountingResult mutationAccounting
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", "SHELL_SCOPE_VIOLATION");
        payload.put("reasonCode", decision.readOnly() ? "UNEXPECTED_SHELL_MUTATION" : "SCOPE_VIOLATION");
        payload.put("message", decision.readOnly()
                ? "Read-only shell command mutated workspace files."
                : "Shell command wrote outside the current accepted change-set.");
        payload.put("retryable", false);
        payload.put("executed", true);
        payload.put("readOnly", decision.readOnly());
        payload.put("commandSummary", decision.commandSummary());
        payload.put("evidence", mutationAccounting.scopeViolationPaths().stream()
                .map(path -> path.toString().replace('\\', '/'))
                .toList());
        payload.put("declaredWritePaths", renderPaths(decision.declaredWritePaths()));
        payload.put("changedPaths", renderPaths(mutationAccounting.changedPaths()));
        payload.put("exitCode", exitCode);
        payload.put("stdout", stdout);
        payload.put("stderr", stderr);
        payload.put("timeoutMs", timeoutMs);
        payload.put("timedOut", timedOut);
        return payload;
    }

    private FatalToolExecutionException fatalScopeViolation(
            ShellCommandDecision decision,
            int exitCode,
            String stdout,
            String stderr,
            long timeoutMs,
            boolean timedOut,
            ToolExecutionContext.ShellMutationAccountingResult mutationAccounting
    ) {
        Map<String, Object> payload = renderScopeViolationPayload(
                decision,
                exitCode,
                stdout,
                stderr,
                timeoutMs,
                timedOut,
                mutationAccounting
        );
        return new FatalToolExecutionException(
                "Shell command wrote outside the current accepted change-set.",
                ToolInvocationResult.failure(payload).render(new com.fasterxml.jackson.databind.ObjectMapper()),
                "请停止当前子任务并回到上层恢复流程，不要继续在当前 tool loop 内扩散越界变更。"
        );
    }

    private Map<String, Object> renderSuccessPayload(
            ShellCommandDecision decision,
            int exitCode,
            String stdout,
            String stderr,
            long timeoutMs,
            boolean timedOut,
            ToolExecutionContext.ShellMutationAccountingResult mutationAccounting
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "shell");
        payload.put("executed", true);
        payload.put("readOnly", decision.readOnly());
        payload.put("commandSummary", decision.commandSummary());
        payload.put("declaredWritePaths", renderPaths(decision.declaredWritePaths()));
        payload.put("changedPaths", renderPaths(mutationAccounting.changedPaths()));
        payload.put("mutationCount", mutationAccounting.changedPaths().size());
        payload.put("exitCode", exitCode);
        payload.put("stdout", stdout);
        payload.put("stderr", stderr);
        payload.put("timeoutMs", timeoutMs);
        payload.put("timedOut", timedOut);
        return payload;
    }

    private Map<String, Object> renderPostExecutionValidationPayload(
            ShellCommandDecision decision,
            int exitCode,
            String stdout,
            String stderr,
            long timeoutMs,
            boolean timedOut,
            String message
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", "SHELL_POST_EXEC_VALIDATION_FAILED");
        payload.put("reasonCode", "MUTATION_CONTRACT_VIOLATION");
        payload.put("message", message == null ? "Shell post-execution validation failed." : message);
        payload.put("retryable", true);
        payload.put("executed", true);
        payload.put("readOnly", decision.readOnly());
        payload.put("commandSummary", decision.commandSummary());
        payload.put("declaredWritePaths", renderPaths(decision.declaredWritePaths()));
        payload.put("exitCode", exitCode);
        payload.put("stdout", stdout);
        payload.put("stderr", stderr);
        payload.put("timeoutMs", timeoutMs);
        payload.put("timedOut", timedOut);
        return payload;
    }

    private List<String> renderPaths(List<Path> paths) {
        return (paths == null ? List.<Path>of() : paths).stream()
                .map(path -> path == null ? "" : path.toString().replace('\\', '/'))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private Thread startPump(InputStream inputStream, ByteArrayOutputStream buffer) {
        Thread thread = new Thread(() -> {
            try (InputStream source = inputStream; ByteArrayOutputStream target = buffer) {
                source.transferTo(target);
            } catch (IOException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void destroyProcessGroup(long processGroupId) {
        if (processGroupId <= 0) {
            return;
        }
        runKill("TERM", processGroupId);
        runKill("KILL", processGroupId);
    }

    private void runKill(String signal, long processGroupId) {
        try {
            new ProcessBuilder(
                    "bash",
                    "-lc",
                    "kill -" + signal + " -- -" + processGroupId + " >/dev/null 2>&1 || true"
            ).start().waitFor(1, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
