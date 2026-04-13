package devflow.agent.validation;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 网页本地资源引用校验支撑。
 */
final class WebResourceValidationSupport {

    private final FileProjectWorkspace workspace;

    WebResourceValidationSupport(FileProjectWorkspace workspace) {
        this.workspace = workspace;
    }

    ValidationStepExecution run(Path projectPath, String reason) {
        List<String> missing = new ArrayList<>();
        for (Path relativePath : workspace.listProjectFiles(projectPath)) {
            if (!ProjectPathSupport.isHtml(relativePath)) {
                continue;
            }
            String content = workspace.readFile(projectPath, relativePath);
            collectMissingLocalAssets(projectPath, relativePath, content, missing);
        }
        if (!missing.isEmpty()) {
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_RESOURCE_LINK_CHECK,
                            ValidationStatus.FAILED,
                            "页面引用的本地资源不存在。",
                            reason + "\nMissing local assets: " + String.join(", ", missing)
                    ),
                    ToolResult.failure(
                            ToolName.RESOURCE_LINK_VERIFY,
                            ToolFailureCode.RESOURCE_MISSING,
                            String.join(", ", missing),
                            "请先补齐缺失的本地资源引用，再继续执行浏览器或脚本验证。"
                    )
            );
        }
        return new ValidationStepExecution(
                new ValidationStepResult(
                        ValidationCapability.WEB_RESOURCE_LINK_CHECK,
                        ValidationStatus.PASSED,
                        "网页资源引用检查通过。",
                        reason + "\nAll local assets referenced by HTML exist."
                ),
                ToolResult.success(ToolName.RESOURCE_LINK_VERIFY)
        );
    }

    private void collectMissingLocalAssets(Path projectPath, Path htmlFile, String content, List<String> missingResources) {
        for (String scriptPath : HtmlDocumentInspector.referencedScriptPaths(content)) {
            collectMissingLocalAsset(projectPath, htmlFile, scriptPath, missingResources);
        }
        for (String stylesheetPath : HtmlDocumentInspector.referencedStylesheetPaths(content)) {
            collectMissingLocalAsset(projectPath, htmlFile, stylesheetPath, missingResources);
        }
    }

    private void collectMissingLocalAsset(Path projectPath, Path htmlFile, String rawRef, List<String> missingResources) {
        if (rawRef == null || rawRef.isBlank() || ProjectPathSupport.isExternalReference(rawRef)) {
            return;
        }
        Path baseDir = htmlFile.getParent() == null ? Path.of("") : htmlFile.getParent();
        Path resolved = projectPath.resolve(baseDir).resolve(rawRef).normalize();
        if (!resolved.startsWith(projectPath.normalize()) || !Files.exists(resolved)) {
            missingResources.add(rawRef);
        }
    }
}
