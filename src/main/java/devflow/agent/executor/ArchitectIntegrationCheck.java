package devflow.agent.executor;

import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ExecutionEntryKind;
import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import devflow.agent.validation.ProjectInspector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ArchitectIntegrationCheck {

    private final FileProjectWorkspace workspace;
    private final ProjectInspector projectInspector;
    private final TreeSitterSupport treeSitterSupport;
    private final ImplementationCompletenessCheck implementationCompletenessCheck;
    private final WebRuntimeWiringCheck webRuntimeWiringCheck;

    public ArchitectIntegrationCheck(FileProjectWorkspace workspace, TreeSitterSupport treeSitterSupport) {
        this.workspace = workspace;
        this.projectInspector = new ProjectInspector(workspace);
        this.treeSitterSupport = treeSitterSupport;
        this.implementationCompletenessCheck = new ImplementationCompletenessCheck(workspace, treeSitterSupport);
        this.webRuntimeWiringCheck = new WebRuntimeWiringCheck(workspace);
    }

    public ArchitectIntegrationCheckResult verify(Path projectPath, ExecutionContract executionContract) {
        return verify(projectPath, executionContract, true);
    }

    public ArchitectIntegrationCheckResult verifyRunnableMilestone(Path projectPath, ExecutionContract executionContract) {
        return verify(projectPath, executionContract, false);
    }

    private ArchitectIntegrationCheckResult verify(
            Path projectPath,
            ExecutionContract executionContract,
            boolean requireImplementationCompleteness
    ) {
        if (executionContract == null || !executionContract.entryRequired()) {
            return ArchitectIntegrationCheckResult.success();
        }
        ProjectFingerprint fingerprint = projectInspector.inspect(projectPath);
        if (!hasResolvableEntry(fingerprint, executionContract)) {
            return ArchitectIntegrationCheckResult.failure(
                    ArchitectIntegrationFailureReason.ENTRY_MISSING,
                    "执行契约要求交付可启动入口，但当前工作区没有解析到可启动入口。"
            );
        }
        if (executionContract.requiresHtmlEntry()) {
            return verifyHtmlEntry(projectPath, fingerprint, executionContract, requireImplementationCompleteness);
        }
        if (requireImplementationCompleteness) {
            ImplementationCompletenessResult completenessResult = implementationCompletenessCheck.inspectProject(projectPath, executionContract);
            if (!completenessResult.passed()) {
                return ArchitectIntegrationCheckResult.failure(
                        ArchitectIntegrationFailureReason.IMPLEMENTATION_INCOMPLETE,
                        completenessResult.summary() + " " + completenessResult.evidenceMarkdown()
                );
            }
        }
        return ArchitectIntegrationCheckResult.success();
    }

    private ArchitectIntegrationCheckResult verifyHtmlEntry(
            Path projectPath,
            ProjectFingerprint fingerprint,
            ExecutionContract executionContract,
            boolean requireImplementationCompleteness
    ) {
        Path entryPath = projectPath.resolve(fingerprint.resolvedHtmlEntryPath());
        if (!Files.exists(entryPath)) {
            return ArchitectIntegrationCheckResult.failure(
                    ArchitectIntegrationFailureReason.ENTRY_MISSING,
                    "执行契约要求交付可启动入口，但解析到的 HTML 入口文件不存在。"
            );
        }
        String content = workspace.readFile(projectPath, Path.of(fingerprint.resolvedHtmlEntryPath()));
        HtmlStructureSnapshot snapshot = treeSitterSupport.inspectHtml(content);
        List<String> issues = new ArrayList<>();
        if (!snapshot.parseSummary().valid()) {
            issues.add("HTML 入口结构未通过解析校验。");
        }
        if (!snapshot.hasHtmlRoot() || !snapshot.hasBody()) {
            issues.add("HTML 入口缺少完整的 html/body 结构。");
        }
        if (executionContract.surfaceRequired() && !hasRuntimeSurface(content, snapshot)) {
            issues.add("执行契约要求存在可见运行表面，但当前 HTML 入口没有可识别的页面表面。");
        }
        if (issues.isEmpty()) {
            WebRuntimeWiringResult wiringResult = webRuntimeWiringCheck.inspect(
                    projectPath,
                    Path.of(fingerprint.resolvedHtmlEntryPath()),
                    snapshot,
                    content
            );
            if (!wiringResult.passed()) {
                return ArchitectIntegrationCheckResult.failure(
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID,
                        wiringResult.summary() + " " + wiringResult.evidenceMarkdown()
                );
            }
            if (requireImplementationCompleteness) {
                ImplementationCompletenessResult completenessResult = implementationCompletenessCheck.inspectProject(projectPath, executionContract);
                if (!completenessResult.passed()) {
                    return ArchitectIntegrationCheckResult.failure(
                            ArchitectIntegrationFailureReason.IMPLEMENTATION_INCOMPLETE,
                            completenessResult.summary() + " " + completenessResult.evidenceMarkdown()
                    );
                }
            }
            return ArchitectIntegrationCheckResult.success();
        }
        return ArchitectIntegrationCheckResult.failure(
                ArchitectIntegrationFailureReason.SURFACE_MISSING,
                String.join(" ", issues)
        );
    }

    private boolean hasResolvableEntry(ProjectFingerprint fingerprint, ExecutionContract executionContract) {
        if (fingerprint == null || executionContract == null || !executionContract.entryRequired()) {
            return false;
        }
        ExecutionEntryKind entryKind = executionContract.normalizedEntryKindEnum();
        if (entryKind.requiresResolvedHtmlEntry()) {
            return fingerprint.hasResolvedHtmlEntry();
        }
        return fingerprint.fileNames().stream()
                .map(path -> path == null ? "" : path.toLowerCase())
                .anyMatch(entryKind::matchesProjectPath);
    }

    private boolean hasRuntimeSurface(String html, HtmlStructureSnapshot snapshot) {
        if (snapshot.hasCanvas() || !snapshot.idSelectors().isEmpty() || !snapshot.buttonSelectors().isEmpty()) {
            return true;
        }
        return HtmlDocumentInspector.hasRuntimeSurfaceTags(html);
    }
}
