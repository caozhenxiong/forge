package devflow.agent.supervisor;

import com.fasterxml.jackson.annotation.JsonProperty;
import devflow.agent.context.ProjectedContext;
import devflow.agent.executor.GenerationFailureReport;
import devflow.agent.orchestrator.GatePolicy;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.util.EnumParsers;
import java.util.List;

/**
 * 统一清洗 supervisor 的模型输出，避免 SupervisorAgent 同时承担：
 * 1. prompt 调用
 * 2. JSON 解析
 * 3. 流程约束纠错
 * 4. delivery policy 兜底
 */
public class SupervisorDecisionSanitizer {

    private final SupervisorPayloadNormalizer payloadNormalizer;
    private final DeliveryPolicySanitizer deliveryPolicySanitizer;

    public SupervisorDecisionSanitizer(SupervisorFallbackPolicy supervisorFallbackPolicy) {
        this.payloadNormalizer = new SupervisorPayloadNormalizer();
        this.deliveryPolicySanitizer = new DeliveryPolicySanitizer(supervisorFallbackPolicy);
    }

    public SupervisorDecision sanitizeDecision(
            DecisionPayload payload,
            RunRecord runRecord,
            StageType currentStage,
            StageType nextStage,
            GatePolicy gatePolicy,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            SupervisorDecision fallback,
            ProjectedContext projectedContext
    ) {
        if (payload == null || payload.action() == null || payload.action().isBlank()) {
            return fallback;
        }
        SupervisorAction action = EnumParsers.parseIgnoreCase(SupervisorAction.class, payload.action(), null);
        if (action == null) {
            return fallback;
        }
        StageType targetStage = payloadNormalizer.parseStage(payload.targetStage());
        FixMode mode = payloadNormalizer.parseFixMode(payload.mode(), reviewResult.fixMode());

        if (reviewResult.decision() == ReviewDecision.APPROVED) {
            if (gatePolicy == GatePolicy.AGENT_PLUS_HUMAN && action != SupervisorAction.REQUEST_HUMAN_REVIEW) {
                return fallback;
            }
            if (!(action == SupervisorAction.ADVANCE_STAGE
                    || action == SupervisorAction.REQUEST_HUMAN_REVIEW
                    || action == SupervisorAction.COMPLETE_RUN)) {
                return fallback;
            }
            if (action == SupervisorAction.REQUEST_HUMAN_REVIEW && gatePolicy != GatePolicy.AGENT_PLUS_HUMAN) {
                return fallback;
            }
            if (action == SupervisorAction.ADVANCE_STAGE && nextStage == null) {
                return fallback;
            }
            if (action == SupervisorAction.COMPLETE_RUN && nextStage != null) {
                return fallback;
            }
            if (action == SupervisorAction.ADVANCE_STAGE) {
                targetStage = nextStage;
            } else {
                targetStage = currentStage;
            }
        } else {
            if (!(action == SupervisorAction.RETRY_STAGE
                    || action == SupervisorAction.ROUTE_TO_REPAIR
                    || action == SupervisorAction.ROLLBACK_STAGE
                    || action == SupervisorAction.FAIL_RUN
                    || action == SupervisorAction.REQUEST_HUMAN_REVIEW)) {
                return fallback;
            }
            if (action == SupervisorAction.ROUTE_TO_REPAIR) {
                targetStage = StageType.IMPLEMENTATION;
                if (!repeatedIssue) {
                    return fallback;
                }
            }
            if (action == SupervisorAction.RETRY_STAGE && targetStage == null) {
                targetStage = fallback.targetStage();
            }
            if (action == SupervisorAction.ROLLBACK_STAGE) {
                if (targetStage == null || targetStage.ordinal() >= currentStage.ordinal()) {
                    return fallback;
                }
            }
            if (action == SupervisorAction.REQUEST_HUMAN_REVIEW) {
                if (gatePolicy != GatePolicy.AGENT_PLUS_HUMAN) {
                    return fallback;
                }
                targetStage = currentStage;
            }
        }

        return new SupervisorDecision(
                action,
                targetStage,
                mode,
                payloadNormalizer.blank(payload.reason()),
                payloadNormalizer.sanitizeGuidanceItems(payloadNormalizer.normalizeList(payload.focus())),
                payloadNormalizer.sanitizeGuidanceItems(payloadNormalizer.normalizeList(payload.constraints())),
                payloadNormalizer.sanitizeGuidanceItems(payloadNormalizer.normalizeList(payload.requiredEvidence())),
                deliveryPolicySanitizer.sanitizeDecisionPolicy(
                        payload.deliveryPolicy(),
                        currentStage,
                        reviewResult,
                        repeatedIssue,
                        projectedContext,
                        fallback.deliveryPolicy()
                ),
                payload.humanRequired() != null && payload.humanRequired()
        );
    }

    public GenerationRecoveryDecision sanitizeGenerationRecoveryDecision(
            GenerationRecoveryPayload payload,
            GenerationRecoveryDecision fallback,
            GenerationFailureReport failureReport
    ) {
        if (payload == null || payload.action() == null || payload.action().isBlank()) {
            return fallback;
        }
        GenerationRecoveryAction action = EnumParsers.parseIgnoreCase(GenerationRecoveryAction.class, payload.action(), null);
        if (action == null) {
            return fallback;
        }
        return new GenerationRecoveryDecision(
                action,
                deliveryPolicySanitizer.sanitizeRecoveryPolicy(payload.deliveryPolicy(), fallback.deliveryPolicy()),
                payloadNormalizer.blank(payload.reason()),
                payloadNormalizer.normalizeList(payload.focus()),
                payloadNormalizer.normalizeList(payload.constraints()),
                payloadNormalizer.normalizeList(payload.requiredEvidence())
        );
    }
}

record DecisionPayload(
        @JsonProperty("action") String action,
        @JsonProperty("targetStage") String targetStage,
        @JsonProperty("mode") String mode,
        @JsonProperty("reason") String reason,
        @JsonProperty("focus") List<String> focus,
        @JsonProperty("constraints") List<String> constraints,
        @JsonProperty("requiredEvidence") List<String> requiredEvidence,
        @JsonProperty("deliveryPolicy") DeliveryPolicyPayload deliveryPolicy,
        @JsonProperty("humanRequired") Boolean humanRequired
) {
}

record DeliveryPolicyPayload(
        @JsonProperty("mode") String mode,
        @JsonProperty("maxFiles") Integer maxFiles,
        @JsonProperty("maxSymbols") Integer maxSymbols,
        @JsonProperty("preferPreciseEditing") Boolean preferPreciseEditing,
        @JsonProperty("forceBacklogSplit") Boolean forceBacklogSplit,
        @JsonProperty("requireVerificationBeforeReview") Boolean requireVerificationBeforeReview
) {
}

record GenerationRecoveryPayload(
        @JsonProperty("action") String action,
        @JsonProperty("reason") String reason,
        @JsonProperty("focus") List<String> focus,
        @JsonProperty("constraints") List<String> constraints,
        @JsonProperty("requiredEvidence") List<String> requiredEvidence,
        @JsonProperty("deliveryPolicy") DeliveryPolicyPayload deliveryPolicy
) {
}
