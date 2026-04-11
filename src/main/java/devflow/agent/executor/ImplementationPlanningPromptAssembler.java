package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;

/**
 * implementation 计划提示词组装器。
 *
 * <p>它负责把 planning 所需的 system/user prompt 收成稳定对象，
 * 避免 `ImplementationPlanner` 继续同时维护长模板、流程循环和 JSON 解析。
 */
final class ImplementationPlanningPromptAssembler {

    private final ImplementationPlanningSystemPromptBuilder systemPromptBuilder;
    private final ImplementationPlanningUserPromptBuilder userPromptBuilder;

    ImplementationPlanningPromptAssembler(int maxFilesPerSubtask, int maxDeliveryPolicyFiles) {
        this.systemPromptBuilder = new ImplementationPlanningSystemPromptBuilder(
                maxFilesPerSubtask,
                maxDeliveryPolicyFiles
        );
        this.userPromptBuilder = new ImplementationPlanningUserPromptBuilder();
    }

    ImplementationPlanningPrompt assemble(
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            String workspaceContext,
            String plannerContextMarkdown,
            String performanceValidationGuidance,
            boolean preferSkeletonFlow,
            DeliveryPolicyEnvelope deliveryPolicy,
            ContractView contractView,
            QualityPlan qualityPlan,
            DocumentLanguage language,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            String requirementCatalog,
            ImplementationContinuationConstraints continuationConstraints,
            String planningFeedback
    ) {
        String system = systemPromptBuilder.build(
                note,
                performanceValidationGuidance,
                preferSkeletonFlow,
                deliveryPolicy,
                fixMode,
                implementationPatchTarget,
                continuationConstraints
        );
        String user = userPromptBuilder.build(
                runRecord,
                analysis,
                prd,
                design,
                note,
                workspaceContext,
                plannerContextMarkdown,
                performanceValidationGuidance,
                deliveryPolicy,
                contractView,
                qualityPlan,
                language,
                requirementCatalog,
                continuationConstraints,
                planningFeedback
        );
        return new ImplementationPlanningPrompt(system, user);
    }
}
