package devflow.agent.executor;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 统一创建 implementation 文件编辑链的 generation observer。
 */
final class ImplementationGenerationObserverFactory {

    GenerationObserver create(
            Path relativePath,
            String strategy,
            DeliveryMode deliveryMode,
            ImplementationEventJournal eventJournal,
            EvidenceSummarizer evidenceSummarizer
    ) {
        return new GenerationObserver() {
            @Override
            public void onAttemptStarted(String operation, int attempt, int maxAttempts) {
                append(eventJournal, ImplementationEventMessages.generationStarted(operation, relativePath, strategy, deliveryMode, attempt, maxAttempts));
            }

            @Override
            public void onAttemptHeartbeat(String operation, int attempt, int maxAttempts) {
                append(eventJournal, ImplementationEventMessages.generationHeartbeat(operation, relativePath, strategy, deliveryMode, attempt, maxAttempts));
            }

            @Override
            public void onAttemptFailed(
                    String operation,
                    int attempt,
                    int maxAttempts,
                    GenerationFailureType failureType,
                    String evidence,
                    GenerationTelemetry telemetry
            ) {
                append(eventJournal, ImplementationEventMessages.generationFailed(
                        operation,
                        relativePath,
                        strategy,
                        deliveryMode,
                        attempt,
                        maxAttempts,
                        failureType,
                        evidenceSummarizer.summarize(evidence, 240)
                ) + GenerationTelemetryFormatter.renderInline(telemetry));
            }

            @Override
            public void onAttemptTimedOut(String operation, int attempt, int maxAttempts, Duration timeout) {
                append(eventJournal, ImplementationEventMessages.generationTimedOut(operation, relativePath, strategy, deliveryMode, attempt, maxAttempts, timeout));
            }

            @Override
            public void onAttemptAborted(String operation, int attempt, int maxAttempts, String reason) {
                append(eventJournal, ImplementationEventMessages.generationAborted(operation, relativePath, strategy, deliveryMode, attempt, maxAttempts, reason));
            }

            @Override
            public void onAttemptSucceeded(String operation, int attempt, int maxAttempts, GenerationTelemetry telemetry) {
                append(
                        eventJournal,
                        ImplementationEventMessages.generationSucceeded(operation, relativePath, strategy, deliveryMode, attempt, maxAttempts)
                                + GenerationTelemetryFormatter.renderInline(telemetry)
                );
            }
        };
    }

    private void append(ImplementationEventJournal eventJournal, String message) {
        if (eventJournal != null) {
            eventJournal.append(message);
        }
    }

    @FunctionalInterface
    interface EvidenceSummarizer {
        String summarize(String evidence, int maxChars);
    }
}
