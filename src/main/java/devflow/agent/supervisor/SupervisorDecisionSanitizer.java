package devflow.agent.supervisor;

import devflow.agent.executor.generation.GenerationFailureReport;

import com.fasterxml.jackson.annotation.JsonProperty;
import devflow.agent.context.ProjectedContext;
import devflow.agent.domain.GatePolicy;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.domain.WorkflowAction;
import devflow.agent.orchestrator.StageFlowPolicy;
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

    private final StageFlowPolicy stageFlowPolicy;
    private final SupervisorPayloadNormalizer payloadNormalizer;
    private final DeliveryPolicySanitizer deliveryPolicySanitizer;

    public SupervisorDecisionSanitizer(
            StageFlowPolicy stageFlowPolicy,
            SupervisorPayloadNormalizer payloadNormalizer,
            SupervisorFallbackPolicy supervisorFallbackPolicy
    ) {
        this.stageFlowPolicy = stageFlowPolicy;
        this.payloadNormalizer = payloadNormalizer;
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
        WorkflowAction action = EnumParsers.parseIgnoreCase(WorkflowAction.class, payload.action(), null);
        if (action == null) {
            return fallback;
        }
        StageType targetStage = payloadNormalizer.parseStage(payload.targetStage());
        FixMode mode = payloadNormalizer.parseFixMode(payload.mode(), reviewResult.fixMode());

        if (reviewResult.decision() == ReviewDecision.APPROVED) {
            if (gatePolicy == GatePolicy.AGENT_PLUS_HUMAN && action != WorkflowAction.REQUEST_HUMAN_REVIEW) {
                return fallback;
            }
            if (!(action == WorkflowAction.ADVANCE_STAGE
                    || action == WorkflowAction.REQUEST_HUMAN_REVIEW
                    || action == WorkflowAction.COMPLETE_RUN)) {
                return fallback;
            }
            if (action == WorkflowAction.REQUEST_HUMAN_REVIEW && gatePolicy != GatePolicy.AGENT_PLUS_HUMAN) {
                return fallback;
            }
            if (action == WorkflowAction.ADVANCE_STAGE && nextStage == null) {
                return fallback;
            }
            if (action == WorkflowAction.COMPLETE_RUN && nextStage != null) {
                return fallback;
            }
            if (action == WorkflowAction.ADVANCE_STAGE) {
                targetStage = nextStage;
            } else {
                targetStage = currentStage;
            }
        } else {
            if (!(action == WorkflowAction.RETRY_STAGE
                    || action == WorkflowAction.ROUTE_TO_REPAIR
                    || action == WorkflowAction.ROLLBACK_STAGE
                    || action == WorkflowAction.FAIL_RUN
                    || action == WorkflowAction.REQUEST_HUMAN_REVIEW)) {
                return fallback;
            }
            if (action == WorkflowAction.ROUTE_TO_REPAIR) {
                StageType repairTarget = stageFlowPolicy.repairTarget(currentStage);
                if (!repeatedIssue || repairTarget == null) {
                    return fallback;
                }
                targetStage = repairTarget;
            }
            if (action == WorkflowAction.RETRY_STAGE && targetStage == null) {
                targetStage = fallback.targetStage();
            }
            if (action == WorkflowAction.ROLLBACK_STAGE) {
                if (targetStage == null || targetStage.ordinal() >= currentStage.ordinal()) {
                    return fallback;
                }
            }
            if (action == WorkflowAction.REQUEST_HUMAN_REVIEW) {
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
