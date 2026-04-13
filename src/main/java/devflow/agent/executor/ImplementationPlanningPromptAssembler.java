package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;
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

    private final ImplementationOutlinePromptBuilder outlinePromptBuilder;
    private final ImplementationSubtaskDetailPromptBuilder detailPromptBuilder;

    ImplementationPlanningPromptAssembler(int maxFilesPerSubtask, int maxDeliveryPolicyFiles) {
        this.outlinePromptBuilder = new ImplementationOutlinePromptBuilder(maxFilesPerSubtask);
        this.detailPromptBuilder = new ImplementationSubtaskDetailPromptBuilder(maxDeliveryPolicyFiles);
    }

    ImplementationPlanningPrompt assembleOutline(
            RunRecord runRecord,
            String note,
            String workspaceContext,
            String plannerContextMarkdown,
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
        String system = outlinePromptBuilder.systemPrompt(
                note,
                deliveryPolicy,
                fixMode,
                implementationPatchTarget,
                continuationConstraints
        );
        String user = outlinePromptBuilder.userPrompt(
                runRecord,
                workspaceContext,
                plannerContextMarkdown,
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

    ImplementationPlanningPrompt assembleSubtaskDetail(
            RunRecord runRecord,
            String workspaceContext,
            ContractView contractView,
            QualityPlan qualityPlan,
            DocumentLanguage language,
            DeliveryPolicyEnvelope deliveryPolicy,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            ImplementationContinuationConstraints continuationConstraints,
            ImplementationOutline outline,
            ImplementationOutlineSubtask subtask,
            String planningFeedback
    ) {
        String system = detailPromptBuilder.systemPrompt(
                deliveryPolicy,
                fixMode,
                implementationPatchTarget,
                continuationConstraints
        );
        String user = detailPromptBuilder.userPrompt(
                runRecord,
                contractView,
                qualityPlan,
                language,
                workspaceContext,
                outline,
                subtask,
                planningFeedback
        );
        return new ImplementationPlanningPrompt(system, user);
    }
}
