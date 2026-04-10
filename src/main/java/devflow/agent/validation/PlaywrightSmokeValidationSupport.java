package devflow.agent.validation;

import devflow.agent.executor.ToolFailureCode;
import devflow.agent.executor.ToolName;
import devflow.agent.executor.ToolResult;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.project.CommandResult;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 浏览器级 smoke validation 支撑。
 */
final class PlaywrightSmokeValidationSupport {

    private final FileProjectWorkspace workspace;

    PlaywrightSmokeValidationSupport(FileProjectWorkspace workspace) {
        this.workspace = workspace;
    }

    ValidationStepExecution run(Path projectPath, ProjectFingerprint fingerprint, String reason) {
        String entry = fingerprint == null ? "" : fingerprint.resolvedHtmlEntryPath();
        if (entry.isBlank()) {
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                            ValidationStatus.SKIPPED,
                            "未探测到 HTML 入口，跳过浏览器 smoke test。",
                            reason
                    ),
                    ToolResult.skipped(
                            ToolName.PLAYWRIGHT_SMOKE,
                            "未探测到 HTML 入口。",
                            "如需浏览器级验证，请先提供有效的 HTML 入口。"
                    )
            );
        }
        Path entryPath = projectPath.resolve(entry);
        if (!Files.exists(entryPath)) {
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                            ValidationStatus.SKIPPED,
                            "探测到的 HTML 入口不存在，跳过浏览器 smoke test。",
                            reason + "\nentry=" + entry
                    ),
                    ToolResult.skipped(
                            ToolName.PLAYWRIGHT_SMOKE,
                            "入口文件不存在: " + entry,
                            "请先修复入口文件路径，再继续浏览器级验证。"
                    )
            );
        }
        Path scriptPath = Path.of("tools", "playwright-smoke", "run-smoke.mjs").toAbsolutePath();
        CommandResult result = workspace.runCommand(
                projectPath,
                List.of("node", scriptPath.toString(), projectPath.toString(), entry),
                Duration.ofMinutes(2)
        );
        if (result.exitCode() != 0) {
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                            ValidationStatus.FAILED,
                            "浏览器级 smoke test 失败。",
                            reason + "\n" + trim(result.stdout()) + "\n" + trim(result.stderr())
                    ),
                    ToolResult.failure(
                            ToolName.PLAYWRIGHT_SMOKE,
                            ToolFailureCode.PLAYWRIGHT_SMOKE_FAILED,
                            trim(result.stdout()) + "\n" + trim(result.stderr()),
                            "请先修复浏览器级运行失败，再继续依赖 smoke test 结论。"
                    )
            );
        }
        return new ValidationStepExecution(
                new ValidationStepResult(
                        ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                        ValidationStatus.PASSED,
                        "浏览器级 smoke test 通过。",
                        reason + "\n" + trim(result.stdout())
                ),
                ToolResult.success(ToolName.PLAYWRIGHT_SMOKE)
        );
    }

    private String trim(String value) {
        return PlaceholderValues.truncateTail(value, 12000);
    }
}
