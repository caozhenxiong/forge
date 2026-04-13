package devflow.agent.supervisor;

import devflow.agent.context.ProjectedContext;
import devflow.agent.domain.StageType;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;

/**
 * delivery policy 清洗器。
 *
 * <p>负责 supervisor 输出里的 delivery policy 兜底、范围钳制和设计阶段回退策略，
 * 避免上层 sanitizer 同时做动作约束和 policy 细节。
 */
final class DeliveryPolicySanitizer {

    private static final int MAX_FILES_UPPER_BOUND = 3;
    private static final int MAX_SYMBOLS_UPPER_BOUND = 12;

    private final SupervisorFallbackPolicy supervisorFallbackPolicy;

    DeliveryPolicySanitizer(SupervisorFallbackPolicy supervisorFallbackPolicy) {
        this.supervisorFallbackPolicy = supervisorFallbackPolicy;
    }

    DeliveryPolicy sanitizeDecisionPolicy(
            DeliveryPolicyPayload payload,
            StageType currentStage,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            ProjectedContext projectedContext,
            DeliveryPolicy fallback
    ) {
        if (payload == null) {
            return fallback;
        }
        DeliveryPolicyMode mode = DeliveryPolicyMode.fromWireValue(payload.mode(), fallback.mode());
        int maxFiles = sanitizeBoundedInt(payload.maxFiles(), fallback.maxFiles(), MAX_FILES_UPPER_BOUND);
        int maxSymbols = sanitizeBoundedInt(payload.maxSymbols(), fallback.maxSymbols(), MAX_SYMBOLS_UPPER_BOUND);
        boolean preferPreciseEditing = payload.preferPreciseEditing() == null
                ? fallback.preferPreciseEditing()
                : payload.preferPreciseEditing();
        boolean forceBacklogSplit = payload.forceBacklogSplit() == null
                ? fallback.forceBacklogSplit()
                : payload.forceBacklogSplit();
        boolean requireVerificationBeforeReview = payload.requireVerificationBeforeReview() == null
                ? fallback.requireVerificationBeforeReview()
                : payload.requireVerificationBeforeReview();
        if (reviewResult.decision() != ReviewDecision.APPROVED && repeatedIssue) {
            forceBacklogSplit = true;
        }
        if (currentStage == StageType.DESIGN && mode == DeliveryPolicyMode.NONE) {
            return supervisorFallbackPolicy.initialImplementationPolicy(projectedContext);
        }
        return new DeliveryPolicy(mode, maxFiles, maxSymbols, preferPreciseEditing, forceBacklogSplit, requireVerificationBeforeReview);
    }

    DeliveryPolicy sanitizeRecoveryPolicy(DeliveryPolicyPayload payload, DeliveryPolicy fallback) {
        if (payload == null) {
            return fallback;
        }
        DeliveryPolicyMode mode = DeliveryPolicyMode.fromWireValue(payload.mode(), fallback.mode());
        int maxFiles = sanitizeBoundedInt(payload.maxFiles(), fallback.maxFiles(), MAX_FILES_UPPER_BOUND);
        int maxSymbols = sanitizeBoundedInt(payload.maxSymbols(), fallback.maxSymbols(), MAX_SYMBOLS_UPPER_BOUND);
        boolean preferPreciseEditing = payload.preferPreciseEditing() == null
                ? fallback.preferPreciseEditing()
                : payload.preferPreciseEditing();
        boolean forceBacklogSplit = payload.forceBacklogSplit() == null
                ? fallback.forceBacklogSplit()
                : payload.forceBacklogSplit();
        boolean requireVerificationBeforeReview = payload.requireVerificationBeforeReview() == null
                ? fallback.requireVerificationBeforeReview()
                : payload.requireVerificationBeforeReview();
        return new DeliveryPolicy(mode, maxFiles, maxSymbols, preferPreciseEditing, forceBacklogSplit, requireVerificationBeforeReview);
    }

    private int sanitizeBoundedInt(Integer candidate, int fallback, int upperBound) {
        return candidate == null || candidate <= 0 || candidate > upperBound
                ? fallback
                : candidate;
    }
}
