package devflow.agent.orchestrator;

import devflow.agent.domain.HumanReviewResolutionContext;
import devflow.agent.domain.StageType;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorDecision;

final class HumanReviewResolutionContextResolver {

    HumanReviewResolutionContext resolveForHumanReview(
            StageType currentStage,
            ReviewResult reviewResult,
            SupervisorDecision supervisorDecision,
            FlowDecision flowDecision
    ) {
        if (reviewResult == null) {
            return HumanReviewResolutionContext.approveStageGate("", "");
        }
        if (isStageGateApproval(reviewResult, flowDecision)) {
            return HumanReviewResolutionContext.approveStageGate(
                    reviewResult.summary(),
                    reviewResult.changeRequest()
            );
        }
        StageType targetStage = resolveRepairTargetStage(currentStage, supervisorDecision, flowDecision);
        return HumanReviewResolutionContext.confirmRepairRoute(
                targetStage,
                reviewResult.fixMode() == null ? FixMode.PATCH : reviewResult.fixMode(),
                reviewResult.implementationPatchTarget(),
                reviewResult.overrideChanges(),
                reviewResult.summary(),
                reviewResult.changeRequest(),
                reviewResult.evidence(),
                reviewResult.actionItems(),
                continuationMode(flowDecision)
        );
    }

    HumanReviewResolutionContext confirmRepairRoute(
            StageType targetStage,
            StageContinuationContext continuationContext
    ) {
        return HumanReviewResolutionContext.confirmRepairRoute(
                targetStage,
                FixMode.PATCH,
                continuationContext.implementationPatchTarget(),
                continuationContext.overrideChanges(),
                continuationContext.summary(),
                continuationContext.changeRequest(),
                continuationContext.evidence(),
                continuationContext.actionItems(),
                continuationContext.continuationMode()
        );
    }

    private boolean isStageGateApproval(ReviewResult reviewResult, FlowDecision flowDecision) {
        if (reviewResult.decision() != ReviewDecision.APPROVED) {
            return false;
        }
        if (reviewResult.fixMode() != FixMode.NONE) {
            return false;
        }
        if (reviewResult.implementationPatchTarget().concretePatch()) {
            return false;
        }
        if (!reviewResult.overrideChanges().isEmpty()) {
            return false;
        }
        return flowDecision == null || flowDecision.transitionDecision() == null
                || flowDecision.transitionDecision().reason() != devflow.agent.loop.TransitionReason.IMPLEMENTATION_BLOCKED_EXHAUSTED_SUBTASK;
    }

    private StageType resolveRepairTargetStage(
            StageType currentStage,
            SupervisorDecision supervisorDecision,
            FlowDecision flowDecision
    ) {
        if (supervisorDecision != null && supervisorDecision.targetStage() != null) {
            return supervisorDecision.targetStage();
        }
        if (flowDecision != null && flowDecision.targetStage() != null) {
            return flowDecision.targetStage();
        }
        return currentStage;
    }

    private ImplementationContinuationMode continuationMode(FlowDecision flowDecision) {
        if (flowDecision == null || flowDecision.transitionDecision() == null) {
            return null;
        }
        return switch (flowDecision.transitionDecision().reason()) {
            case IMPLEMENTATION_PATCH_CONTINUE -> ImplementationContinuationMode.PATCH_CONTINUE;
            case IMPLEMENTATION_BLOCKED_EXHAUSTED_SUBTASK -> ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK;
            default -> ImplementationContinuationMode.MID_PLAN_CONTINUE;
        };
    }
}
