package devflow.agent.executor;

import devflow.agent.review.ImplementationPatchTarget;

public record ArchitectIntegrationCheckResult(
        boolean passed,
        ArchitectIntegrationFailureReason failureReason,
        String details,
        ImplementationPatchTarget implementationPatchTarget,
        HtmlRuntimeOwnershipContract runtimeContract
) {

    public static ArchitectIntegrationCheckResult success() {
        return new ArchitectIntegrationCheckResult(true, null, "", ImplementationPatchTarget.NONE, null);
    }

    public static ArchitectIntegrationCheckResult failure(ArchitectIntegrationFailureReason failureReason, String details) {
        return failure(failureReason, details, ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION, null);
    }

    public static ArchitectIntegrationCheckResult failure(
            ArchitectIntegrationFailureReason failureReason,
            String details,
            ImplementationPatchTarget implementationPatchTarget
    ) {
        return failure(failureReason, details, implementationPatchTarget, null);
    }

    public static ArchitectIntegrationCheckResult failure(
            ArchitectIntegrationFailureReason failureReason,
            String details,
            ImplementationPatchTarget implementationPatchTarget,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        return new ArchitectIntegrationCheckResult(
                false,
                failureReason,
                details,
                implementationPatchTarget == null ? ImplementationPatchTarget.NONE : implementationPatchTarget,
                runtimeContract
        );
    }
}
