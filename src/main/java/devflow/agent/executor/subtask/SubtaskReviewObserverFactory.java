package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.generation.GenerationObserver;
import devflow.agent.executor.generation.GenerationTelemetry;
import devflow.agent.executor.generation.GenerationTelemetryFormatter;

import devflow.agent.i18n.PlaceholderValues;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.implementation.ImplementationEventMessages;
/**
 * 负责子任务 review 阶段的可观测事件映射。
 * 这样 verifier 的事件协议不会继续堆在验证编排类里。
 */
public final class SubtaskReviewObserverFactory {

    GenerationObserver create(String subtaskTitle, ImplementationEventJournal eventJournal) {
        return new GenerationObserver() {
            @Override
            public void onAttemptStarted(String operation, int attempt, int maxAttempts) {
                appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.reviewStarted(subtaskTitle, attempt, maxAttempts)
                );
            }

            @Override
            public void onAttemptHeartbeat(String operation, int attempt, int maxAttempts) {
                appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.reviewHeartbeat(subtaskTitle, attempt, maxAttempts)
                );
            }

            @Override
            public void onAttemptTimedOut(String operation, int attempt, int maxAttempts, java.time.Duration timeout) {
                appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.reviewTimedOut(subtaskTitle, attempt, maxAttempts, timeout)
                );
            }

            @Override
            public void onAttemptAborted(String operation, int attempt, int maxAttempts, String reason) {
                appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.reviewAborted(subtaskTitle, attempt, maxAttempts, reason)
                );
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
                appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.reviewFailed(
                                subtaskTitle,
                                attempt,
                                maxAttempts,
                                failureType,
                                PlaceholderValues.truncateMiddle(evidence, 240)
                        ) + GenerationTelemetryFormatter.renderInline(telemetry)
                );
            }

            @Override
            public void onAttemptSucceeded(String operation, int attempt, int maxAttempts, GenerationTelemetry telemetry) {
                appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.reviewSucceeded(subtaskTitle, attempt, maxAttempts)
                                + GenerationTelemetryFormatter.renderInline(telemetry)
                );
            }
        };
    }

    private void appendImplementationEvent(ImplementationEventJournal eventJournal, String message) {
        if (eventJournal == null) {
            return;
        }
        eventJournal.append(message);
    }
}
