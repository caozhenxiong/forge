package devflow.agent.supervisor;

public record DeliveryPolicy(
        DeliveryPolicyMode mode,
        Integer maxFiles,
        Integer maxSymbols,
        boolean preferPreciseEditing,
        boolean forceBacklogSplit,
        boolean requireVerificationBeforeReview
) {

    public static DeliveryPolicy balanced(DeliveryPolicyMode mode) {
        return new DeliveryPolicy(mode, 2, 4, true, false, true);
    }

    public static DeliveryPolicy patchSafe() {
        return new DeliveryPolicy(DeliveryPolicyMode.PATCH, 1, 2, true, false, true);
    }

    public static DeliveryPolicy recoverySafe() {
        return new DeliveryPolicy(DeliveryPolicyMode.PATCH, 1, 1, true, false, true);
    }

    public static DeliveryPolicy skeletonFirst() {
        return new DeliveryPolicy(DeliveryPolicyMode.SKELETON, 2, 3, true, true, true);
    }

    public static DeliveryPolicy reworkSafe() {
        return new DeliveryPolicy(DeliveryPolicyMode.REWORK, 2, 6, true, true, true);
    }

    public DeliveryPolicy withPreferPreciseEditing(boolean value) {
        return new DeliveryPolicy(mode, maxFiles, maxSymbols, value, forceBacklogSplit, requireVerificationBeforeReview);
    }
}
