package devflow.agent.executor;

import devflow.agent.review.ImplementationPatchTarget;

public record ArchitectIntegrationCheckResult(
        ArchitectIntegrationCheckScope scope,
        boolean passed,
        ArchitectIntegrationFailureReason failureReason,
        String details,
        ImplementationPatchTarget implementationPatchTarget,
        HtmlRuntimeOwnershipContract runtimeContract
) {

    public static ArchitectIntegrationCheckResult success() {
        return success(ArchitectIntegrationCheckScope.STAGE_COMPLETION);
    }

    public static ArchitectIntegrationCheckResult success(ArchitectIntegrationCheckScope scope) {
        return new ArchitectIntegrationCheckResult(
                scope == null ? ArchitectIntegrationCheckScope.STAGE_COMPLETION : scope,
                true,
                null,
                "",
                ImplementationPatchTarget.NONE,
                null
        );
    }

    public static ArchitectIntegrationCheckResult failure(ArchitectIntegrationFailureReason failureReason, String details) {
        return failure(
                ArchitectIntegrationCheckScope.STAGE_COMPLETION,
                failureReason,
                details,
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                null
        );
    }

    public static ArchitectIntegrationCheckResult failure(
            ArchitectIntegrationFailureReason failureReason,
            String details,
            ImplementationPatchTarget implementationPatchTarget
    ) {
        return failure(
                ArchitectIntegrationCheckScope.STAGE_COMPLETION,
                failureReason,
                details,
                implementationPatchTarget,
                null
        );
    }

    public static ArchitectIntegrationCheckResult failure(
            ArchitectIntegrationCheckScope scope,
            ArchitectIntegrationFailureReason failureReason,
            String details,
            ImplementationPatchTarget implementationPatchTarget
    ) {
        return failure(scope, failureReason, details, implementationPatchTarget, null);
    }

    public static ArchitectIntegrationCheckResult failure(
            ArchitectIntegrationCheckScope scope,
            ArchitectIntegrationFailureReason failureReason,
            String details,
            ImplementationPatchTarget implementationPatchTarget,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        return new ArchitectIntegrationCheckResult(
                scope == null ? ArchitectIntegrationCheckScope.STAGE_COMPLETION : scope,
                false,
                failureReason,
                details,
                implementationPatchTarget == null ? ImplementationPatchTarget.NONE : implementationPatchTarget,
                runtimeContract
        );
    }
}
