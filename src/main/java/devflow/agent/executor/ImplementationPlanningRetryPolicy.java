package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.text.TextCanonicalizer;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 统一维护 implementation planning 的内部重试规则。
 *
 * <p>planning 失败属于 implementation 内部的局部问题，应在同一个 stage attempt 内部收敛，
 * 而不是把“重新规划”再伪装成外层 review/retry。这里专门负责识别可恢复的 planning 失败、
 * 执行有限次重试，并输出稳定的 exhaustion 错误。
 */
class ImplementationPlanningRetryPolicy {

    private final ImplementationPlanner implementationPlanner;
    private final int maxInternalPlanRetries;

    ImplementationPlanningRetryPolicy(
            ImplementationPlanner implementationPlanner,
            int maxInternalPlanRetries
    ) {
        this.implementationPlanner = implementationPlanner;
        this.maxInternalPlanRetries = maxInternalPlanRetries;
    }

    ImplementationPlan planWithInternalRetries(
            Path projectPath,
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
            ProjectFingerprint fingerprint,
            DocumentLanguage language,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            String requirementCatalog,
            ImplementationContinuationConstraints continuationConstraints,
            Consumer<String> retryEventPublisher
    ) {
        String planningNote = note;
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxInternalPlanRetries; attempt++) {
            try {
                return implementationPlanner.plan(
                        projectPath,
                        runRecord,
                        analysis,
                        prd,
                        design,
                        planningNote,
                        workspaceContext,
                        plannerContextMarkdown,
                        performanceValidationGuidance,
                        preferSkeletonFlow,
                        deliveryPolicy,
                        contractView,
                        qualityPlan,
                        fingerprint,
                        language,
                        fixMode,
                        implementationPatchTarget,
                        requirementCatalog,
                        continuationConstraints
                );
            } catch (RuntimeException exception) {
                lastException = exception;
                if (!isRecoverablePlanningFailure(exception)) {
                    throw exception;
                }
                if (retryEventPublisher != null) {
                    GenerationTelemetry telemetry = exception instanceof ImplementationPlanningException planningException
                            ? planningException.telemetry()
                            : null;
                    retryEventPublisher.accept(
                            "IMPLEMENTATION planning retry attempt=%d/%d reason=%s"
                                    .formatted(
                                            attempt,
                                            maxInternalPlanRetries,
                                            summarizeFailure(exception == null ? "" : exception.getMessage())
                                    ) + GenerationTelemetryFormatter.renderInline(telemetry)
                    );
                }
                if (attempt == maxInternalPlanRetries) {
                    break;
                }
                planningNote = mergeFeedback(note, exception.getMessage());
            }
        }
        throw new IllegalStateException(
                language.choose(
                        "Implementation planning exhausted internal retries: ",
                        "Implementation planning exhausted internal retries: "
                ) + blankIfNull(lastException == null ? "" : lastException.getMessage()),
                lastException
        );
    }

    private boolean isRecoverablePlanningFailure(RuntimeException exception) {
        if (!(exception instanceof ImplementationPlanningException planningException)) {
            return false;
        }
        return planningException.reason().recoverable();
    }

    private String summarizeFailure(String value) {
        String normalized = TextCanonicalizer.collapseWhitespace(blankIfNull(value));
        return PlaceholderValues.truncateInline(normalized, 240);
    }

    private String mergeFeedback(String inheritedFeedback, String newFeedback) {
        String inherited = blankIfNull(inheritedFeedback).trim();
        String fresh = blankIfNull(newFeedback).trim();
        if (inherited.isBlank()) {
            return fresh;
        }
        if (fresh.isBlank()) {
            return inherited;
        }
        return inherited + "\n\n" + fresh;
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
