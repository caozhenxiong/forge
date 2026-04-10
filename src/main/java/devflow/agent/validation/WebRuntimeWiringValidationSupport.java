package devflow.agent.validation;

import devflow.agent.executor.ToolFailureCode;
import devflow.agent.executor.ToolName;
import devflow.agent.executor.ToolResult;
import devflow.agent.executor.WebRuntimeWiringCheck;
import devflow.agent.executor.WebRuntimeWiringResult;
import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;

/**
 * 网页入口 runtime wiring 校验支撑。
 *
 * <p>这层关注“入口是否真的把运行时接成了唯一可运行所有权”：
 * companion orphan、dual-track、入口未接线都在这里统一失败。
 */
final class WebRuntimeWiringValidationSupport {

    private final FileProjectWorkspace workspace;
    private final ProjectInspector projectInspector;
    private final TreeSitterSupport treeSitterSupport;
    private final WebRuntimeWiringCheck webRuntimeWiringCheck;

    WebRuntimeWiringValidationSupport(FileProjectWorkspace workspace) {
        this.workspace = workspace;
        this.projectInspector = new ProjectInspector(workspace);
        this.treeSitterSupport = new TreeSitterSupport();
        this.webRuntimeWiringCheck = new WebRuntimeWiringCheck(workspace);
    }

    ValidationStepExecution run(Path projectPath, ProjectFingerprint fingerprint, String reason) {
        ProjectFingerprint effectiveFingerprint = fingerprint != null ? fingerprint : projectInspector.inspect(projectPath);
        if (effectiveFingerprint == null || !effectiveFingerprint.hasResolvedHtmlEntry()) {
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_RUNTIME_WIRING_CHECK,
                            ValidationStatus.PASSED,
                            "未解析到 HTML 入口，跳过 runtime wiring 检查。",
                            reason
                    ),
                    ToolResult.success(ToolName.RUNTIME_WIRING_VERIFY)
            );
        }
        Path htmlEntryPath = Path.of(effectiveFingerprint.resolvedHtmlEntryPath()).normalize();
        String htmlSource = workspace.readFile(projectPath, htmlEntryPath);
        HtmlStructureSnapshot snapshot = treeSitterSupport.inspectHtml(htmlSource);
        WebRuntimeWiringResult result = webRuntimeWiringCheck.inspect(projectPath, htmlEntryPath, snapshot, htmlSource);
        if (!result.passed()) {
            String details = result.summary();
            if (!result.evidenceMarkdown().isBlank()) {
                details = details + "\n" + result.evidenceMarkdown();
            }
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_RUNTIME_WIRING_CHECK,
                            ValidationStatus.FAILED,
                            "网页入口 runtime 接线不完整或所有权冲突。",
                            reason + "\n" + details.trim()
                    ),
                    ToolResult.failure(
                            ToolName.RUNTIME_WIRING_VERIFY,
                            ToolFailureCode.RUNTIME_WIRING_INVALID,
                            result.evidenceMarkdown(),
                            "请先修复 HTML 入口与 runtime 脚本的接线/所有权问题，再继续 smoke test。"
                    )
            );
        }
        return new ValidationStepExecution(
                new ValidationStepResult(
                        ValidationCapability.WEB_RUNTIME_WIRING_CHECK,
                        ValidationStatus.PASSED,
                        "网页 runtime 接线检查通过。",
                        reason + "\nHTML runtime wiring ok."
                ),
                ToolResult.success(ToolName.RUNTIME_WIRING_VERIFY)
        );
    }
}
