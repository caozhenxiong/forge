package devflow.agent.supervisor;

import devflow.agent.orchestrator.StageType;
import devflow.agent.review.FixMode;
import java.util.List;

public record SupervisorDecision(
        SupervisorAction action,
        StageType targetStage,
        FixMode mode,
        String reason,
        List<String> focus,
        List<String> constraints,
        List<String> requiredEvidence,
        DeliveryPolicy deliveryPolicy,
        boolean humanRequired
) {
    public SupervisorDecision {
        mode = mode == null ? FixMode.NONE : mode;
        reason = reason == null ? "" : reason;
        focus = focus == null ? List.of() : List.copyOf(focus);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        requiredEvidence = requiredEvidence == null ? List.of() : List.copyOf(requiredEvidence);
        deliveryPolicy = deliveryPolicy == null
                ? DeliveryPolicy.balanced(DeliveryPolicyMode.fromWireValue(mode.name(), DeliveryPolicyMode.NONE))
                : deliveryPolicy;
    }
}
