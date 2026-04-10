package devflow.agent.executor;

public interface GenerationObserver {

    default void onAttemptStarted(String operation, int attempt, int maxAttempts) {
    }

    default void onAttemptHeartbeat(String operation, int attempt, int maxAttempts) {
    }

    default void onAttemptFailed(String operation, int attempt, int maxAttempts, GenerationFailureType failureType, String evidence) {
    }

    default void onAttemptFailed(
            String operation,
            int attempt,
            int maxAttempts,
            GenerationFailureType failureType,
            String evidence,
            GenerationTelemetry telemetry
    ) {
        onAttemptFailed(operation, attempt, maxAttempts, failureType, evidence);
    }

    default void onAttemptTimedOut(String operation, int attempt, int maxAttempts, java.time.Duration timeout) {
    }

    default void onAttemptAborted(String operation, int attempt, int maxAttempts, String reason) {
    }

    default void onAttemptSucceeded(String operation, int attempt, int maxAttempts) {
    }

    default void onAttemptSucceeded(String operation, int attempt, int maxAttempts, GenerationTelemetry telemetry) {
        onAttemptSucceeded(operation, attempt, maxAttempts);
    }
}
