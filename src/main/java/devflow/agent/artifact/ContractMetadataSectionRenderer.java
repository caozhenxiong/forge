package devflow.agent.artifact;

import devflow.agent.context.ContractMetadataKeys;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.i18n.PlaceholderValues;

/**
 * 统一渲染文档里的 Contract Metadata 章节。
 *
 * <p>这里同时输出 runtime.* 与 validation.*，避免文档后处理只保留运行时键，
 * 导致 validation authority 在主链里被直接冲掉。
 */
final class ContractMetadataSectionRenderer {

    String render(int sectionNumber, ExecutionContract executionContract, ValidationMetadata validationMetadata) {
        ExecutionContract normalizedExecutionContract = executionContract == null
                ? new ExecutionContract(false, "unspecified", false, false, java.util.List.of()).normalized()
                : executionContract.normalized();
        ValidationMetadata normalizedValidationMetadata = validationMetadata == null
                ? ValidationMetadata.empty()
                : validationMetadata;
        return """
                ## %d. Contract Metadata

                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                """.formatted(
                sectionNumber,
                ContractMetadataKeys.RUNTIME_ENTRY_REQUIRED,
                normalizedExecutionContract.entryRequired(),
                ContractMetadataKeys.RUNTIME_ENTRY_KIND,
                blank(normalizedExecutionContract.entryKind()),
                ContractMetadataKeys.RUNTIME_ENTRY_PACKAGING_MODE,
                blank(normalizedExecutionContract.entryPackagingMode()),
                ContractMetadataKeys.RUNTIME_RUNTIME_OWNERSHIP_MODE,
                blank(normalizedExecutionContract.runtimeOwnershipMode()),
                ContractMetadataKeys.RUNTIME_LAUNCH_REQUIRED,
                normalizedExecutionContract.launchRequired(),
                ContractMetadataKeys.RUNTIME_SURFACE_REQUIRED,
                normalizedExecutionContract.surfaceRequired(),
                ContractMetadataKeys.RUNTIME_ACCEPTANCE_SIGNALS,
                normalizedExecutionContract.acceptanceSignals() == null || normalizedExecutionContract.acceptanceSignals().isEmpty()
                        ? PlaceholderValues.machineNone()
                        : String.join(", ", normalizedExecutionContract.acceptanceSignals()),
                ContractMetadataKeys.VALIDATION_PERFORMANCE_MEASUREMENT_REQUIRED,
                normalizedValidationMetadata.performanceMeasurementRequired(),
                ContractMetadataKeys.VALIDATION_PAGE_LOAD_MAX_MS,
                scalar(normalizedValidationMetadata.pageLoadMaxMs()),
                ContractMetadataKeys.VALIDATION_INTERACTION_MAX_MS,
                scalar(normalizedValidationMetadata.interactionMaxMs())
        ).trim();
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? PlaceholderValues.machineNone() : value.trim();
    }

    private String scalar(Integer value) {
        return value == null ? PlaceholderValues.machineNone() : value.toString();
    }
}
