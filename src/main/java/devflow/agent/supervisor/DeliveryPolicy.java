package devflow.agent.supervisor;

public record DeliveryPolicy(
        String mode,
        Integer maxFiles,
        Integer maxSymbols,
        boolean preferPreciseEditing,
        boolean forceBacklogSplit,
        boolean requireVerificationBeforeReview
) {

    public static DeliveryPolicy balanced(String mode) {
        return new DeliveryPolicy(mode, 2, 4, true, false, true);
    }

    public static DeliveryPolicy patchSafe() {
        return new DeliveryPolicy("PATCH", 1, 2, true, false, true);
    }

    public static DeliveryPolicy skeletonFirst() {
        return new DeliveryPolicy("SKELETON", 2, 3, true, true, true);
    }

    public static DeliveryPolicy reworkSafe() {
        return new DeliveryPolicy("REWORK", 2, 6, true, true, true);
    }
}
