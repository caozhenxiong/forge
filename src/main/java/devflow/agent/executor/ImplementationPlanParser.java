package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.AuthoritativeCoverageCatalog;
import devflow.agent.context.ExecutionContract;
import devflow.agent.review.FixMode;

/**
 * implementation plan 解析与修复器。
 *
 * <p>它把 JSON 解析、repair 与 normalized plan 构造收成独立层，
 * 避免 `ImplementationPlanner` 一边维护 turn loop，一边维护 plan schema 细节。
 */
final class ImplementationPlanParser {

    private final LlmProvider llmProvider;
    private final StructuredPayloadReader structuredPayloadReader;
    private final int maxPlanParseAttempts;
    private final ImplementationPlanRepairSupport repairSupport;
    private final ImplementationPlanNormalizationSupport normalizationSupport;

    ImplementationPlanParser(LlmProvider llmProvider, ObjectMapper objectMapper, int maxPlanParseAttempts) {
        this.llmProvider = llmProvider;
        this.structuredPayloadReader = new StructuredPayloadReader(objectMapper);
        this.maxPlanParseAttempts = maxPlanParseAttempts;
        this.repairSupport = new ImplementationPlanRepairSupport(llmProvider);
        this.normalizationSupport = new ImplementationPlanNormalizationSupport();
    }

    ImplementationPlan parsePlanWithRepair(
            String response,
            FixMode fixMode,
            boolean preferSkeletonFlow,
            DeliveryPolicyEnvelope deliveryPolicy,
            AuthoritativeCoverageCatalog authoritativeCoverageCatalog,
            ExecutionContract executionContract,
            ImplementationContinuationConstraints continuationConstraints
    ) {
        String candidate = response;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxPlanParseAttempts; attempt++) {
            try {
                return parsePlan(
                        candidate,
                        fixMode,
                        preferSkeletonFlow,
                        deliveryPolicy,
                        authoritativeCoverageCatalog,
                        executionContract,
                        continuationConstraints
                );
            } catch (Exception exception) {
                lastException = exception;
                if (attempt == maxPlanParseAttempts) {
                    break;
                }
                candidate = repairSupport.repairPlan(candidate, exception);
            }
        }
        String failureReason = lastException == null || lastException.getMessage() == null || lastException.getMessage().isBlank()
                ? "unknown parse error"
                : lastException.getMessage();
        throw new ImplementationPlanningException(
                ImplementationPlanningFailureReason.PLAN_PARSE_FAILED,
                "Failed to parse implementation plan (" + failureReason + "): " + response,
                lastException,
                llmProvider.consumeLastTelemetry()
        );
    }

    private ImplementationPlan parsePlan(
            String response,
            FixMode fixMode,
            boolean preferSkeletonFlow,
            DeliveryPolicyEnvelope deliveryPolicy,
            AuthoritativeCoverageCatalog authoritativeCoverageCatalog,
            ExecutionContract executionContract,
            ImplementationContinuationConstraints continuationConstraints
    ) throws Exception {
        ImplementationPlan plan = structuredPayloadReader.readJsonObject(response, ImplementationPlan.class);
        return normalizationSupport.normalize(
                plan,
                fixMode,
                preferSkeletonFlow,
                deliveryPolicy,
                authoritativeCoverageCatalog,
                executionContract,
                continuationConstraints
        );
    }
}
