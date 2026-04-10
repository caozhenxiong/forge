package devflow.agent.orchestrator;

import devflow.agent.protocol.ExecutionDirectiveNarrativeRenderer;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.review.FixMode;
import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.supervisor.DeliveryPolicyMode;
import devflow.agent.supervisor.SupervisorDecision;
import java.util.List;

/**
 * 阶段修订说明构造器。
 *
 * <p>负责把 review 结论、修订要求和 supervisor 决策归一成最终 revision note，
 * 避免阶段回流支持类继续直接拼装 directive payload。
 */
final class StageRevisionNoteBuilder {

    String build(
            FixMode fixMode,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            SupervisorDecision supervisorDecision,
            List<String> requiredCapabilitySurfaces
    ) {
        DeliveryPolicy deliveryPolicy = supervisorDecision == null
                ? DeliveryPolicy.balanced(DeliveryPolicyMode.NONE)
                : supervisorDecision.deliveryPolicy();
        return ExecutionDirectiveNarrativeRenderer.renderRevisionNote(
                new ExecutionDirectivePayload(
                        (fixMode == null ? FixMode.PATCH : fixMode).name(),
                        false,
                        false,
                        deliveryPolicy.mode() == null ? null : deliveryPolicy.mode().wireValue(),
                        deliveryPolicy.maxFiles(),
                        deliveryPolicy.maxSymbols(),
                        deliveryPolicy.preferPreciseEditing(),
                        deliveryPolicy.forceBacklogSplit(),
                        deliveryPolicy.requireVerificationBeforeReview(),
                        supervisorDecision == null ? java.util.List.of() : supervisorDecision.requiredEvidence(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        requiredCapabilitySurfaces == null ? java.util.List.of() : requiredCapabilitySurfaces,
                        java.util.List.of(),
                        summary,
                        changeRequest,
                        evidence,
                        actionItems,
                        supervisorDecision == null || supervisorDecision.action() == null ? null : supervisorDecision.action().name(),
                        supervisorDecision == null ? null : supervisorDecision.reason(),
                        supervisorDecision == null ? java.util.List.of() : supervisorDecision.focus(),
                        supervisorDecision == null ? java.util.List.of() : supervisorDecision.constraints(),
                        null,
                        null,
                        null,
                        null
                ),
                nullToEmpty(summary),
                nullToEmpty(changeRequest),
                nullToEmpty(evidence),
                nullToEmpty(actionItems)
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
