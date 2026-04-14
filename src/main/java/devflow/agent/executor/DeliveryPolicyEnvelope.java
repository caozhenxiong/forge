package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.implementation.ImplementationExecutionPolicy;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

/**
 * implementation 阶段内部使用的交付策略包装对象。
 * 它把 delivery mode、文件数、符号数和验证要求集中到一起，避免常量散落。
 */
public record DeliveryPolicyEnvelope(
        DeliveryMode mode,
        int maxFiles,
        int maxSymbols,
        boolean preferPreciseEditing,
        boolean forceBacklogSplit,
        boolean requireVerificationBeforeReview,
        List<String> requiredEvidence
) {
    public static DeliveryPolicyEnvelope defaultPolicy() {
        ImplementationExecutionPolicy executionPolicy = new ImplementationExecutionPolicy();
        EditUnitPlanningPolicy editUnitPlanningPolicy = new EditUnitPlanningPolicy();
        return new DeliveryPolicyEnvelope(
                DeliveryMode.PATCH,
                executionPolicy.maxFilesPerSubtask(),
                editUnitPlanningPolicy.maxSymbolsPerUnit(),
                true,
                false,
                true,
                List.of()
        );
    }
}
