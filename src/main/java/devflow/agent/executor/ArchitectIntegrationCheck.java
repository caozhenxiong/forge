package devflow.agent.executor;

import devflow.agent.context.ContractRuntimeOwnershipMode;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ExecutionEntryKind;
import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.review.ImplementationPatchTarget;
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
                    "执行契约要求交付可启动入口，但当前工作区没有解析到可启动入口。",
                    ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
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
                        completenessResult.summary() + " " + completenessResult.evidenceMarkdown(),
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
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
                    "执行契约要求交付可启动入口，但解析到的 HTML 入口文件不存在。",
                    ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
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
                RuntimeWiringPatchDecision patchDecision = wiringResult.patchDecision();
                return ArchitectIntegrationCheckResult.failure(
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID,
                        wiringResult.summary() + " " + wiringResult.evidenceMarkdown(),
                        patchDecision == null ? ImplementationPatchTarget.PATCH_RUNTIME_WIRING : patchDecision.patchTarget(),
                        patchDecision == null ? null : patchDecision.runtimeContract()
                );
            }
            ArchitectIntegrationCheckResult contractConsistencyResult = verifyRuntimeContractConsistency(
                    Path.of(fingerprint.resolvedHtmlEntryPath()),
                    executionContract,
                    wiringResult
            );
            if (!contractConsistencyResult.passed()) {
                return contractConsistencyResult;
            }
            if (requireImplementationCompleteness) {
                ImplementationCompletenessResult completenessResult = implementationCompletenessCheck.inspectProject(projectPath, executionContract);
                if (!completenessResult.passed()) {
                    return ArchitectIntegrationCheckResult.failure(
                            ArchitectIntegrationFailureReason.IMPLEMENTATION_INCOMPLETE,
                            completenessResult.summary() + " " + completenessResult.evidenceMarkdown(),
                            ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                    );
                }
            }
            return ArchitectIntegrationCheckResult.success();
        }
        return ArchitectIntegrationCheckResult.failure(
                ArchitectIntegrationFailureReason.SURFACE_MISSING,
                String.join(" ", issues),
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
        );
    }

    /**
     * 这里只校验“当前实现解析出来的 runtime 所有权是否与 approved contract 一致”，
     * 不根据实现形态本身做价值判断。contract 没显式声明 ownership 时，保持中立。
     */
    private ArchitectIntegrationCheckResult verifyRuntimeContractConsistency(
            Path htmlEntryPath,
            ExecutionContract executionContract,
            WebRuntimeWiringResult wiringResult
    ) {
        ContractRuntimeOwnershipMode contractOwnershipMode = executionContract.normalizedRuntimeOwnershipModeEnum();
        RuntimeOwnershipMode expectedOwnership = contractOwnershipMode.toRuntimeOwnershipMode();
        if (expectedOwnership == null) {
            return ArchitectIntegrationCheckResult.success();
        }
        HtmlEntryRuntimeOwnershipInspection inspection = wiringResult == null ? null : wiringResult.ownershipInspection();
        HtmlRuntimeOwnershipContract actualContract = inspection == null ? null : inspection.runtimeContract();
        RuntimeOwnershipMode actualOwnership = actualContract == null ? null : actualContract.runtimeOwnership();
        if (actualOwnership == expectedOwnership) {
            return ArchitectIntegrationCheckResult.success();
        }
        HtmlRuntimeOwnershipContract expectedRuntimeContract = expectedRuntimeContract(htmlEntryPath, expectedOwnership, inspection);
        String details = """
                approved contract 要求 runtimeOwnershipMode=%s，但当前实现解析为 %s。
                %s
                """.formatted(
                contractOwnershipMode.wireValue(),
                ContractRuntimeOwnershipMode.fromRuntimeOwnershipMode(actualOwnership).wireValue(),
                inspection == null ? "" : inspection.evidenceMarkdown()
        ).trim();
        return ArchitectIntegrationCheckResult.failure(
                ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID,
                details,
                ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                expectedRuntimeContract
        );
    }

    private HtmlRuntimeOwnershipContract expectedRuntimeContract(
            Path htmlEntryPath,
            RuntimeOwnershipMode expectedOwnership,
            HtmlEntryRuntimeOwnershipInspection inspection
    ) {
        if (expectedOwnership == RuntimeOwnershipMode.INLINE_HOST) {
            return HtmlRuntimeOwnershipContract.inlineHost(htmlEntryPath);
        }
        java.util.LinkedHashSet<Path> runtimePaths = new java.util.LinkedHashSet<>();
        if (inspection != null && inspection.runtimeContract() != null) {
            runtimePaths.addAll(inspection.runtimeContract().runtimePaths());
        }
        if (inspection != null) {
            runtimePaths.addAll(inspection.referencedRuntimePaths());
            runtimePaths.addAll(inspection.availableRuntimePaths());
        }
        return HtmlRuntimeOwnershipContract.externalCompanion(
                htmlEntryPath,
                runtimePaths.stream().sorted().toList()
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
