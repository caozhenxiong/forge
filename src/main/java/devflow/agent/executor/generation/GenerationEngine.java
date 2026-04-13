package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenerationEngine {

    public <T> T execute(GenerationSpec<T> spec) {
        String retryFeedback = "";
        GenerationFailureType lastFailureType = GenerationFailureType.VALIDATION_FAILED;
        String lastFailureEvidence = "";
        for (int attempt = 1; attempt <= spec.maxAttempts(); attempt++) {
            int currentAttempt = attempt;
            spec.observer().onAttemptStarted(spec.operation(), attempt, spec.maxAttempts());
            AtomicBoolean running = new AtomicBoolean(true);
            Thread heartbeatThread = startHeartbeat(spec, attempt, running);
            String attemptRetryFeedback = retryFeedback;
            CompletableFuture<GenerationAttemptResult<T>> future = new CompletableFuture<>();
            Thread workerThread = Thread.ofVirtual()
                    .name("generation-attempt-" + spec.operation() + "-" + currentAttempt)
                    .start(() -> {
                        try {
                            future.complete(spec.attemptExecutor().execute(currentAttempt, attemptRetryFeedback));
                        } catch (Throwable throwable) {
                            future.completeExceptionally(throwable);
                        }
                    });
            try {
                GenerationAttemptResult<T> outcome = future
                        .orTimeout(spec.attemptTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .join();
                GenerationTelemetry telemetry = resolveTelemetry(spec, outcome.telemetry());
                if (outcome.success()) {
                    running.set(false);
                    joinHeartbeat(heartbeatThread);
                    spec.observer().onAttemptSucceeded(spec.operation(), attempt, spec.maxAttempts(), telemetry);
                    return outcome.result();
                }
                lastFailureType = outcome.failureType() == null ? lastFailureType : outcome.failureType();
                lastFailureEvidence = blank(outcome.evidence());
                retryFeedback = blank(outcome.retryFeedback());
                running.set(false);
                joinHeartbeat(heartbeatThread);
                spec.observer().onAttemptFailed(spec.operation(), attempt, spec.maxAttempts(), lastFailureType, lastFailureEvidence, telemetry);
                if (outcome.terminalFailure()) {
                    break;
                }
            } catch (CompletionException exception) {
                GenerationTelemetry telemetry = resolveTelemetry(spec, null);
                Throwable cause = exception.getCause();
                if (cause instanceof TimeoutException) {
                    lastFailureType = GenerationFailureType.ATTEMPT_TIMEOUT;
                    lastFailureEvidence = "Attempt timed out after %d seconds".formatted(spec.attemptTimeout().toSeconds());
                    retryFeedback = "";
                    future.cancel(true);
                    workerThread.interrupt();
                    running.set(false);
                    joinHeartbeat(heartbeatThread);
                    spec.observer().onAttemptTimedOut(spec.operation(), attempt, spec.maxAttempts(), spec.attemptTimeout());
                    spec.observer().onAttemptAborted(spec.operation(), attempt, spec.maxAttempts(), "timeout");
                    spec.observer().onAttemptFailed(spec.operation(), attempt, spec.maxAttempts(), lastFailureType, lastFailureEvidence, telemetry);
                    continue;
                }
                Exception actual = cause instanceof Exception actualException
                        ? actualException
                        : new IllegalStateException(cause == null ? "Unknown generation failure" : cause.getMessage(), cause);
                lastFailureType = spec.exceptionClassifier().classify(actual);
                lastFailureEvidence = actual.getMessage() == null ? actual.getClass().getSimpleName() : actual.getMessage();
                retryFeedback = "";
                running.set(false);
                joinHeartbeat(heartbeatThread);
                spec.observer().onAttemptFailed(spec.operation(), attempt, spec.maxAttempts(), lastFailureType, lastFailureEvidence, telemetry);
            } catch (Exception exception) {
                GenerationTelemetry telemetry = resolveTelemetry(spec, null);
                lastFailureType = spec.exceptionClassifier().classify(exception);
                lastFailureEvidence = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                retryFeedback = "";
                running.set(false);
                joinHeartbeat(heartbeatThread);
                spec.observer().onAttemptFailed(spec.operation(), attempt, spec.maxAttempts(), lastFailureType, lastFailureEvidence, telemetry);
            }
        }
        throw spec.failureFactory().create(lastFailureType, lastFailureEvidence);
    }

    private Thread startHeartbeat(GenerationSpec<?> spec, int attempt, AtomicBoolean running) {
        Duration interval = spec.heartbeatInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return null;
        }
        Thread heartbeat = Thread.ofVirtual()
                .name("generation-heartbeat-" + spec.operation() + "-" + attempt)
                .start(() -> {
                    while (running.get()) {
                        try {
                            Thread.sleep(interval);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        if (running.get()) {
                            spec.observer().onAttemptHeartbeat(spec.operation(), attempt, spec.maxAttempts());
                        }
                    }
                });
        return heartbeat;
    }

    private void joinHeartbeat(Thread heartbeatThread) {
        if (heartbeatThread == null) {
            return;
        }
        try {
            heartbeatThread.join(10);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private GenerationTelemetry resolveTelemetry(GenerationSpec<?> spec, GenerationTelemetry telemetry) {
        if (telemetry != null) {
            return telemetry;
        }
        return spec.telemetrySupplier().get();
    }
}
