package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.context.ContextAccessProfile;
import devflow.agent.context.ContextLayerAssembler;
import devflow.agent.context.ContextViews;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ExecutionEntryKind;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.protocol.ExecutionDirectiveNarrativeRenderer;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.QualityPlanFactory;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.validation.ProjectFingerprint;
import devflow.agent.validation.ProjectInspector;
import java.nio.file.Path;

/**
 * 负责 implementation 执行前的上下文准备。
 *
 * <p>这层统一处理语言识别、delivery policy、contract、共享上下文与工作区摘要，
 * 避免执行器在真正编排前还要自己做大量解析与默认值判断。
 */
class ImplementationContextResolver {

    private final FileProjectWorkspace workspace;
    private final ProjectInspector projectInspector;
    private final ContractExtractor contractExtractor;
    private final ContextLayerAssembler contextLayerAssembler;
    private final ImplementationDirectiveResolver directiveResolver;
    private final ImplementationSharedContextFactory sharedContextFactory;
    private final QualityPlanFactory qualityPlanFactory;
    private final ImplementationContinuationConstraintResolver continuationConstraintResolver;

    ImplementationContextResolver(
            FileProjectWorkspace workspace,
            ProjectInspector projectInspector,
            ContractExtractor contractExtractor,
            ContextLayerAssembler contextLayerAssembler,
            ObjectMapper objectMapper,
            int maxFilesPerSubtask,
            int maxDeliveryPolicyFiles
    ) {
        this.workspace = workspace;
        this.projectInspector = projectInspector;
        this.contractExtractor = contractExtractor;
        this.contextLayerAssembler = contextLayerAssembler;
        this.directiveResolver = new ImplementationDirectiveResolver(maxFilesPerSubtask, maxDeliveryPolicyFiles);
        this.sharedContextFactory = new ImplementationSharedContextFactory();
        this.qualityPlanFactory = new QualityPlanFactory();
        this.continuationConstraintResolver = new ImplementationContinuationConstraintResolver(objectMapper);
    }

    ImplementationExecutionContext resolve(
            Path projectPath,
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            ContractView authoritativeContractView,
            String previousStateJson
    ) {
        // 这里集中收 implementation 开跑前的全部输入，后续执行器只消费这个结果。
        DocumentLanguage language = DocumentLanguage.detect(runRecord.goal(), runRecord.constraints(), note);
        String workspaceContext = workspace.collectContext(projectPath, 24, 12000, 60000);
        String performanceValidationGuidance = extractPerformanceValidationGuidance(design);
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(note);
        DeliveryPolicyEnvelope deliveryPolicy = directiveResolver.resolveDeliveryPolicy(directives);
        FixMode fixMode = directiveResolver.resolveFixMode(directives);
        ImplementationPatchTarget implementationPatchTarget =
                directiveResolver.resolveImplementationPatchTarget(directives);
        java.util.List<FileChange> overrideChanges = directiveResolver.resolveOverrideChanges(directives);
        ProjectFingerprint fingerprint = projectInspector.inspect(projectPath);
        ContractView contractView = authoritativeContractView == null
                ? contractExtractor.extractContractView(runRecord.goal(), runRecord.constraints(), analysis, prd, design)
                : authoritativeContractView;
        ValidationMetadata validationMetadata = contractExtractor.extractValidationMetadata(prd, design);
        QualityPlan qualityPlan = qualityPlanFactory.build(
                projectPath,
                fingerprint,
                contractView,
                validationMetadata,
                null,
                directives.requiredCapabilitySurfaces()
        );
        ImplementationContinuationConstraints continuationConstraints =
                continuationConstraintResolver.resolve(previousStateJson, fingerprint);
        boolean preferSkeletonFlow = fixMode != FixMode.PATCH && (deliveryPolicy.mode() == DeliveryMode.SKELETON
                || (deliveryPolicy.forceBacklogSplit() && deliveryPolicy.mode() != DeliveryMode.PATCH)
                || shouldPreferProgressiveDelivery(contractView, fingerprint));
        SharedContextBundle sharedContextBundle = sharedContextFactory.build(
                runRecord,
                note,
                workspaceContext,
                contractView,
                directives
        );
        ContextViews contextViews = contextLayerAssembler.assemble(
                runRecord,
                devflow.agent.orchestrator.StageType.IMPLEMENTATION,
                contractView,
                "",
                contractView == null ? "" : contractView.toMarkdown(language),
                contractView == null ? "" : contractView.toMarkdown(language),
                "",
                "",
                sharedContextBundle.repairSummary(),
                workspaceContext,
                java.util.List.of()
        );
        return new ImplementationExecutionContext(
                language,
                workspaceContext,
                contextViews.forProfile(ContextAccessProfile.PLANNER).toMarkdown(language),
                contextViews.forProfile(ContextAccessProfile.CODER).toMarkdown(language),
                performanceValidationGuidance,
                deliveryPolicy,
                fixMode,
                implementationPatchTarget,
                overrideChanges,
                fingerprint,
                contractView,
                qualityPlan,
                preferSkeletonFlow,
                sharedContextBundle,
                sharedContextFactory.renderProductRequirementCatalog(
                        contractView == null ? null : contractView.productContract(),
                        qualityPlan,
                        language
                ),
                continuationConstraints
        );
    }

    private String extractPerformanceValidationGuidance(String design) {
        ValidationMetadata metadata = contractExtractor.extractValidationMetadata("", design);
        if (!metadata.performanceMeasurementRequired()) {
            return "";
        }
        return ExecutionDirectiveNarrativeRenderer.renderPerformanceValidationGuidance(
                metadata.pageLoadMaxMs(),
                metadata.interactionMaxMs()
        );
    }

    private boolean shouldPreferProgressiveDelivery(ContractView contractView, ProjectFingerprint fingerprint) {
        if (contractView == null || contractView.executionContract() == null) {
            return false;
        }
        ExecutionContract executionContract = contractView.executionContract();
        if (!executionContract.entryRequired()) {
            return false;
        }
        if (executionContract.surfaceRequired()) {
            return true;
        }
        return executionContract.launchRequired() && !hasResolvableEntry(fingerprint, executionContract);
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

}
