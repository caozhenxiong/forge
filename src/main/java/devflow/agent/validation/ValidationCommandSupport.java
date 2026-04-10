package devflow.agent.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.executor.ToolFailureCode;
import devflow.agent.executor.ToolName;
import devflow.agent.executor.ToolResult;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.project.CommandResult;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 命令型 validation step 支撑。
 *
 * <p>负责：
 * 1. 运行构建/测试命令；
 * 2. 识别 node 脚本是否存在；
 * 3. 产出稳定的 `ValidationStepExecution + ToolResult`。
 */
final class ValidationCommandSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PACKAGE_JSON_SCRIPTS_FIELD = "scripts";

    private final FileProjectWorkspace workspace;

    ValidationCommandSupport(FileProjectWorkspace workspace) {
        this.workspace = workspace;
    }

    ValidationStepExecution runCommandStep(
            Path projectPath,
            ValidationCapability capability,
            List<String> command,
            String reason,
            boolean failOnMissingScript
    ) {
        String scriptName = !failOnMissingScript ? resolveNodeScriptName(command) : null;
        if (scriptName != null && !packageDefinesScript(projectPath, scriptName)) {
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            capability,
                            ValidationStatus.SKIPPED,
                            "项目未定义该脚本，跳过。",
                            renderCommandDetails(command, reason, new CommandResult(0, "(skipped)", "(script not defined)"))
                    ),
                    ToolResult.skipped(
                            ToolName.COMMAND_VALIDATE,
                            "脚本未定义: " + scriptName,
                            "后续如果需要该验证能力，应先在 package.json 定义对应脚本。"
                    )
            );
        }
        CommandResult result = workspace.runCommand(projectPath, command, Duration.ofMinutes(10));
        if (result.exitCode() == 0) {
            return new ValidationStepExecution(
                    new ValidationStepResult(capability, ValidationStatus.PASSED, "命令执行通过。", renderCommandDetails(command, reason, result)),
                    ToolResult.success(ToolName.COMMAND_VALIDATE)
            );
        }
        return new ValidationStepExecution(
                new ValidationStepResult(capability, ValidationStatus.FAILED, "命令执行失败，exitCode=" + result.exitCode(), renderCommandDetails(command, reason, result)),
                ToolResult.failure(
                        ToolName.COMMAND_VALIDATE,
                        ToolFailureCode.COMMAND_FAILED,
                        "命令执行失败，exitCode=" + result.exitCode(),
                        "请先修复当前命令失败，再继续依赖该自检结果。"
                )
        );
    }

    private String resolveNodeScriptName(List<String> command) {
        if (command == null || command.isEmpty()) {
            return null;
        }
        if (command.size() >= 3
                && ("npm".equals(command.getFirst()) || "pnpm".equals(command.getFirst()))
                && "run".equals(command.get(1))) {
            return command.get(2);
        }
        if (command.size() >= 2 && "yarn".equals(command.getFirst())) {
            return command.get(1);
        }
        return null;
    }

    private boolean packageDefinesScript(Path projectPath, String scriptName) {
        if (scriptName == null || scriptName.isBlank()) {
            return false;
        }
        Path packageJson = projectPath.resolve(ProjectPathSupport.primaryNodeManifestFileName());
        if (!Files.exists(packageJson)) {
            return false;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(Files.readString(packageJson));
            JsonNode scripts = root.path(PACKAGE_JSON_SCRIPTS_FIELD);
            return scripts.isObject() && scripts.hasNonNull(scriptName);
        } catch (IOException exception) {
            return false;
        }
    }

    private String renderCommandDetails(List<String> command, String reason, CommandResult result) {
        return """
                reason: %s
                command: `%s`
                exitCode: %d

                stdout:
                %s

                stderr:
                %s
                """.formatted(reason, String.join(" ", command), result.exitCode(), trim(result.stdout()), trim(result.stderr()));
    }

    private String trim(String value) {
        return PlaceholderValues.truncateTail(value, 12000);
    }
}
