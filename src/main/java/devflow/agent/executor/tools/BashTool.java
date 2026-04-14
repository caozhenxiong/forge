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
                ToolFailureCode failureCode = deniedFailureCode(decision);
                context.appendEvent("实现阶段｜shell｜拒绝｜原因=%s｜可重试=%s｜命令=%s｜证据=%s"
                        .formatted(
                                decision.reasonCode(),
                                decision.retryable(),
                                decision.commandSummary(),
                                decision.evidence()
                ));
                context.recordToolFailure(
                        "Bash",
                        diagnosticPath(decision),
                        failureCode,
                        shellFailureEvidence(decision, decision.message())
                );
                return ToolInvocationResult.failure(renderDeniedPayload(decision, failureCode));
            }
            long timeoutMs;
            try {
                timeoutMs = context.resolveShellTimeout(input.timeout());
                context.assertShellWriteTargets(decision.pathIntents());
            } catch (IllegalArgumentException validationException) {
                ToolFailureCode failureCode = ToolFailureCode.COMMAND_FAILED;
                context.appendEvent("实现阶段｜shell｜执行前校验失败｜命令=%s｜原因=%s"
                        .formatted(decision.commandSummary(), validationException.getMessage()));
                context.recordToolFailure(
                        "Bash",
                        diagnosticPath(decision),
                        failureCode,
                        shellFailureEvidence(decision, validationException.getMessage())
                );
                return ToolInvocationResult.failure(renderPreExecutionDeniedPayload(decision, validationException.getMessage(), failureCode));
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
                ToolFailureCode failureCode = ToolFailureCode.COMMAND_FAILED;
                context.appendEvent("实现阶段｜shell｜后置校验失败｜命令=%s｜原因=%s"
                        .formatted(decision.commandSummary(), validationException.getMessage()));
                context.recordToolFailure(
                        "Bash",
                        diagnosticPath(decision),
                        failureCode,
                        shellFailureEvidence(decision, validationException.getMessage())
                );
                return ToolInvocationResult.failure(renderPostExecutionValidationPayload(
                        decision,
                        exitCode,
                        stdout.toString(),
                        stderr.toString(),
                        timeoutMs,
                        !finished,
                        validationException.getMessage(),
                        failureCode
                ));
            }
            if (!mutationAccounting.scopeViolationPaths().isEmpty()) {
                ToolFailureCode failureCode = ToolFailureCode.TARGET_SCOPE_VIOLATION;
                context.appendEvent("实现阶段｜shell｜越界写入｜命令=%s｜越界路径=%s"
                        .formatted(decision.commandSummary(), mutationAccounting.scopeViolationPaths()));
                context.recordToolFailure(
                        "Bash",
                        mutationAccounting.scopeViolationPaths().getFirst(),
                        failureCode,
                        shellFailureEvidence(
                                decision,
                                "scopeViolationPaths=" + renderPaths(mutationAccounting.scopeViolationPaths())
                        )
                );
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
            context.recordToolFailure("Bash", Path.of(""), ToolFailureCode.COMMAND_FAILED, exception.getMessage());
            return ToolInvocationResult.failure(Map.of(
                    "type", "error",
                    "failureCode", ToolFailureCode.COMMAND_FAILED.name(),
                    "message", exception.getMessage() == null ? "Shell command was interrupted." : exception.getMessage()
            ));
        } catch (IllegalArgumentException exception) {
            context.recordToolFailure("Bash", Path.of(""), ToolFailureCode.COMMAND_FAILED, exception.getMessage());
            return ToolInvocationResult.failure(Map.of(
                    "type", "error",
                    "failureCode", ToolFailureCode.COMMAND_FAILED.name(),
                    "message", exception.getMessage()
            ));
        } catch (Exception exception) {
            context.recordToolFailure("Bash", Path.of(""), ToolFailureCode.COMMAND_FAILED, exception.getMessage());
            return ToolInvocationResult.failure(Map.of(
                    "type", "error",
                    "failureCode", ToolFailureCode.COMMAND_FAILED.name(),
                    "message", exception.getMessage()
            ));
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

    private Map<String, Object> renderDeniedPayload(ShellCommandDecision decision, ToolFailureCode failureCode) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", "SHELL_COMMAND_DENIED");
        payload.put("failureCode", failureCode == null ? null : failureCode.name());
        payload.put("reasonCode", decision.reasonCode());
        payload.put("message", decision.message());
        payload.put("retryable", decision.retryable());
        payload.put("executed", false);
        payload.put("readOnly", decision.readOnly());
        payload.put("commandSummary", decision.commandSummary());
        payload.put("evidence", decision.evidence());
        payload.put("declaredWritePaths", renderPaths(decision.declaredTargetPaths()));
        return payload;
    }

    private Map<String, Object> renderPreExecutionDeniedPayload(
            ShellCommandDecision decision,
            String message,
            ToolFailureCode failureCode
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", "SHELL_COMMAND_DENIED");
        payload.put("failureCode", failureCode == null ? null : failureCode.name());
        payload.put("reasonCode", "PRE_EXEC_VALIDATION_FAILED");
        payload.put("message", message == null ? "Shell command failed pre-execution validation." : message);
        payload.put("retryable", true);
        payload.put("executed", false);
        payload.put("readOnly", decision.readOnly());
        payload.put("commandSummary", decision.commandSummary());
        payload.put("evidence", decision.evidence());
        payload.put("declaredWritePaths", renderPaths(decision.declaredTargetPaths()));
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
        payload.put("failureCode", ToolFailureCode.TARGET_SCOPE_VIOLATION.name());
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
        payload.put("declaredWritePaths", renderPaths(decision.declaredTargetPaths()));
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
            String message,
            ToolFailureCode failureCode
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", "SHELL_POST_EXEC_VALIDATION_FAILED");
        payload.put("failureCode", failureCode == null ? null : failureCode.name());
        payload.put("reasonCode", "MUTATION_CONTRACT_VIOLATION");
        payload.put("message", message == null ? "Shell post-execution validation failed." : message);
        payload.put("retryable", true);
        payload.put("executed", true);
        payload.put("readOnly", decision.readOnly());
        payload.put("commandSummary", decision.commandSummary());
        payload.put("declaredWritePaths", renderPaths(decision.declaredTargetPaths()));
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

    private ToolFailureCode deniedFailureCode(ShellCommandDecision decision) {
        if (decision != null && "SCOPE_VIOLATION".equalsIgnoreCase(decision.reasonCode())) {
            return ToolFailureCode.TARGET_SCOPE_VIOLATION;
        }
        return ToolFailureCode.COMMAND_FAILED;
    }

    private Path diagnosticPath(ShellCommandDecision decision) {
        if (decision == null || decision.declaredTargetPaths() == null || decision.declaredTargetPaths().isEmpty()) {
            return Path.of("");
        }
        return decision.declaredTargetPaths().getFirst().normalize();
    }

    private String shellFailureEvidence(ShellCommandDecision decision, String message) {
        StringBuilder builder = new StringBuilder();
        if (decision != null) {
            builder.append("command=").append(decision.commandSummary());
            if (decision.reasonCode() != null && !decision.reasonCode().isBlank()) {
                builder.append(", reasonCode=").append(decision.reasonCode());
            }
            if (decision.evidence() != null && !decision.evidence().isEmpty()) {
                builder.append(", decisionEvidence=").append(decision.evidence());
            }
            if (decision.declaredTargetPaths() != null && !decision.declaredTargetPaths().isEmpty()) {
                builder.append(", declaredWritePaths=").append(renderPaths(decision.declaredTargetPaths()));
            }
        }
        if (message != null && !message.isBlank()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("message=").append(message.trim());
        }
        return builder.toString();
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
