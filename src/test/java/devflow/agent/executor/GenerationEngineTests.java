package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationAttemptResult;
import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.generation.GenerationFailureException;
import devflow.agent.executor.generation.GenerationFailureReport;
import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.generation.GenerationObserver;
import devflow.agent.executor.generation.GenerationSpec;
import devflow.agent.executor.generation.GenerationTelemetry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationEngineTests {

    @Test
    void timesOutAndEmitsAbortSignals() {
        GenerationEngine engine = new GenerationEngine();
        AtomicBoolean timedOut = new AtomicBoolean(false);
        AtomicBoolean aborted = new AtomicBoolean(false);

        GenerationFailureException exception = assertThrows(
                GenerationFailureException.class,
                () -> engine.execute(new GenerationSpec<>(
                        "timeout-test",
                        1,
                        Duration.ofMillis(10),
                        Duration.ofMillis(50),
                        new GenerationObserver() {
                            @Override
                            public void onAttemptTimedOut(String operation, int attempt, int maxAttempts, Duration timeout) {
                                timedOut.set(true);
                            }

                            @Override
                            public void onAttemptAborted(String operation, int attempt, int maxAttempts, String reason) {
                                aborted.set(true);
                            }
                        },
                        (attempt, retryFeedback) -> {
                            Thread.sleep(200);
                            return GenerationAttemptResult.success("ok");
                        },
                        exception1 -> GenerationFailureType.MODEL_INVOCATION_FAILED,
                        (failureType, evidence) -> new GenerationFailureException(
                                new GenerationFailureReport(
                                        "timeout-test",
                                        "PATCH",
                                        "test",
                                        failureType,
                                        1,
                                        false,
                                        "generation timed out",
                                        evidence,
                                        "retry"
                                )
                        )
                ))
        );

        assertTrue(timedOut.get(), "超时时应产生 timed-out 事件");
        assertTrue(aborted.get(), "超时时应产生 aborted 事件");
        assertTrue(exception.report().failureType() == GenerationFailureType.ATTEMPT_TIMEOUT);
    }

    @Test
    void forwardsTelemetryFromSupplierToObserverOnSuccess() {
        GenerationEngine engine = new GenerationEngine();
        AtomicReference<GenerationTelemetry> captured = new AtomicReference<>();

        String result = engine.execute(new GenerationSpec<>(
                "telemetry-success",
                1,
                Duration.ZERO,
                new GenerationObserver() {
                    @Override
                    public void onAttemptSucceeded(String operation, int attempt, int maxAttempts, GenerationTelemetry telemetry) {
                        captured.set(telemetry);
                    }
                },
                (attempt, retryFeedback) -> GenerationAttemptResult.success("ok"),
                exception -> GenerationFailureType.MODEL_INVOCATION_FAILED,
                (failureType, evidence) -> new GenerationFailureException(
                        new GenerationFailureReport(
                                "telemetry-success",
                                "PATCH",
                                "test",
                                failureType,
                                1,
                                false,
                                "generation failed",
                                evidence,
                                "retry"
                        )
                ),
                () -> new GenerationTelemetry("qwen3-coder:30b", "IMPLEMENTATION", 1200, 1100, 900, 36864, 1800, 33664, 1400, 1300, "stop")
        ));

        assertEquals("ok", result);
        assertEquals(1100, captured.get().actualPromptTokens());
        assertEquals(900, captured.get().outputTokens());
    }
}
