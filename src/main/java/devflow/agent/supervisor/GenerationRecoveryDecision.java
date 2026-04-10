package devflow.agent.supervisor;

import java.util.List;

public record GenerationRecoveryDecision(
        GenerationRecoveryAction action,
        DeliveryPolicy deliveryPolicy,
        String reason,
        List<String> focus,
        List<String> constraints,
        List<String> requiredEvidence
) {
    public GenerationRecoveryDecision {
        deliveryPolicy = deliveryPolicy == null ? DeliveryPolicy.patchSafe() : deliveryPolicy;
        reason = reason == null ? "" : reason;
        focus = focus == null ? List.of() : List.copyOf(focus);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        requiredEvidence = requiredEvidence == null ? List.of() : List.copyOf(requiredEvidence);
    }
}
