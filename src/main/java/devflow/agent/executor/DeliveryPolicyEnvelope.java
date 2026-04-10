package devflow.agent.executor;

import java.util.List;

/**
 * implementation 阶段内部使用的交付策略包装对象。
 * 它把 delivery mode、文件数、符号数和验证要求集中到一起，避免常量散落。
 */
record DeliveryPolicyEnvelope(
        DeliveryMode mode,
        int maxFiles,
        int maxSymbols,
        boolean preferPreciseEditing,
        boolean forceBacklogSplit,
        boolean requireVerificationBeforeReview,
        List<String> requiredEvidence
) {
    static DeliveryPolicyEnvelope defaultPolicy() {
        return new DeliveryPolicyEnvelope(
                DeliveryMode.PATCH,
                ImplementationExecutionPolicy.maxFilesPerSubtask(),
                EditUnitPlanningPolicy.maxSymbolsPerUnit(),
                true,
                false,
                true,
                List.of()
        );
    }
}
