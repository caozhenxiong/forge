package devflow.agent.executor.implementation.planning;

import devflow.agent.context.ContractView;
import devflow.agent.domain.RunRecord;
import devflow.agent.executor.DeliveryPolicyEnvelope;
import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.validation.ProjectFingerprint;

public record PlanningRequest(
        RunRecord runRecord,
        String note,
        String workspaceContext,
        String plannerContextMarkdown,
        String performanceValidationGuidance,
        boolean preferSkeletonFlow,
        DeliveryPolicyEnvelope deliveryPolicy,
        ContractView contractView,
        QualityPlan qualityPlan,
        ProjectFingerprint fingerprint,
        DocumentLanguage language,
        FixMode fixMode,
        ImplementationPatchTarget implementationPatchTarget,
        String requirementCatalog,
        ImplementationContinuationConstraints continuationConstraints,
        ImplementationEventJournal eventJournal
) {
}
