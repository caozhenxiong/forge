package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.PatchFailure;

public record GenerationAttemptResult<T>(
        boolean success,
        T result,
        GenerationFailureType failureType,
        String evidence,
        String retryFeedback,
        boolean terminalFailure,
        GenerationTelemetry telemetry
) {

    public static <T> GenerationAttemptResult<T> success(T result) {
        return success(result, null);
    }

    public static <T> GenerationAttemptResult<T> success(T result, GenerationTelemetry telemetry) {
        return new GenerationAttemptResult<>(true, result, null, "", "", false, telemetry);
    }

    public static <T> GenerationAttemptResult<T> failure(
            GenerationFailureType failureType,
            String evidence,
            String retryFeedback
    ) {
        return failure(failureType, evidence, retryFeedback, null);
    }

    public static <T> GenerationAttemptResult<T> failure(
            GenerationFailureType failureType,
            String evidence,
            String retryFeedback,
            GenerationTelemetry telemetry
    ) {
        return new GenerationAttemptResult<>(
                false,
                null,
                failureType,
                evidence == null ? "" : evidence,
                retryFeedback == null ? "" : retryFeedback,
                false,
                telemetry
        );
    }

    public static <T> GenerationAttemptResult<T> failure(
            PatchFailure patchFailure,
            String retryFeedback
    ) {
        return failure(patchFailure, retryFeedback, null);
    }

    public static <T> GenerationAttemptResult<T> failure(
            PatchFailure patchFailure,
            String retryFeedback,
            GenerationTelemetry telemetry
    ) {
        return patchFailure == null
                ? failure(GenerationFailureType.VALIDATION_FAILED, "", retryFeedback, telemetry)
                : failure(patchFailure.failureType(), patchFailure.evidence(), retryFeedback, telemetry);
    }

    public static <T> GenerationAttemptResult<T> terminalFailure(
            GenerationFailureType failureType,
            String evidence,
            String retryFeedback
    ) {
        return terminalFailure(failureType, evidence, retryFeedback, null);
    }

    public static <T> GenerationAttemptResult<T> terminalFailure(
            GenerationFailureType failureType,
            String evidence,
            String retryFeedback,
            GenerationTelemetry telemetry
    ) {
        return new GenerationAttemptResult<>(
                false,
                null,
                failureType,
                evidence == null ? "" : evidence,
                retryFeedback == null ? "" : retryFeedback,
                true,
                telemetry
        );
    }

    public static <T> GenerationAttemptResult<T> terminalFailure(
            PatchFailure patchFailure,
            String retryFeedback
    ) {
        return terminalFailure(patchFailure, retryFeedback, null);
    }

    public static <T> GenerationAttemptResult<T> terminalFailure(
            PatchFailure patchFailure,
            String retryFeedback,
            GenerationTelemetry telemetry
    ) {
        return patchFailure == null
                ? terminalFailure(GenerationFailureType.VALIDATION_FAILED, "", retryFeedback, telemetry)
                : terminalFailure(patchFailure.failureType(), patchFailure.evidence(), retryFeedback, telemetry);
    }
}
