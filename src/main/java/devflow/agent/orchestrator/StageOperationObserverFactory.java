package devflow.agent.orchestrator;

import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.generation.GenerationObserver;
import devflow.agent.executor.generation.GenerationTelemetry;
import devflow.agent.executor.generation.GenerationTelemetryFormatter;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.text.TextCanonicalizer;
import java.time.Duration;

/**
 * 统一创建阶段 generation/review 的可观测事件观察器。
 *
 * <p>这样阶段执行器只负责“调用哪个阶段操作”，
 * 不再继续内嵌两套几乎重复的 start/heartbeat/fail 日志模板。
 */
final class StageOperationObserverFactory {

    private final EventLogStore eventLogStore;
    private static final String GENERATION_LABEL = "生成";
    private static final String REVIEW_LABEL = "评审";

    StageOperationObserverFactory(EventLogStore eventLogStore) {
        this.eventLogStore = eventLogStore;
    }

    GenerationObserver generationObserver(
            java.nio.file.Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            int attemptNumber
    ) {
        return observer(
                projectPath,
                runRecord,
                stageType,
                attemptNumber,
                GENERATION_LABEL
        );
    }

    GenerationObserver reviewObserver(
            java.nio.file.Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            int attemptNumber
    ) {
        return observer(
                projectPath,
                runRecord,
                stageType,
                attemptNumber,
                REVIEW_LABEL
        );
    }

    private GenerationObserver observer(
            java.nio.file.Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            int attemptNumber,
            String operationKind
    ) {
        return new GenerationObserver() {
            @Override
            public void onAttemptStarted(String operation, int attempt, int maxAttempts) {
                eventLogStore.append(
                        projectPath,
                        runRecord.runId(),
                        WorkflowEventMessages.stageOperationStarted(operationKind, stageType, attemptNumber, attempt, maxAttempts)
                );
            }

            @Override
            public void onAttemptHeartbeat(String operation, int attempt, int maxAttempts) {
                eventLogStore.append(
                        projectPath,
                        runRecord.runId(),
                        WorkflowEventMessages.stageOperationHeartbeat(operationKind, stageType, attemptNumber, attempt, maxAttempts)
                );
            }

            @Override
            public void onAttemptTimedOut(String operation, int attempt, int maxAttempts, Duration timeout) {
                eventLogStore.append(
                        projectPath,
                        runRecord.runId(),
                        WorkflowEventMessages.stageOperationTimedOut(
                                operationKind,
                                stageType,
                                attemptNumber,
                                attempt,
                                maxAttempts,
                                timeout.toSeconds()
                        )
                );
            }

            @Override
            public void onAttemptAborted(String operation, int attempt, int maxAttempts, String reason) {
                eventLogStore.append(
                        projectPath,
                        runRecord.runId(),
                        WorkflowEventMessages.stageOperationAborted(
                                operationKind,
                                stageType,
                                attemptNumber,
                                attempt,
                                maxAttempts,
                                reason
                        )
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
                eventLogStore.append(
                        projectPath,
                        runRecord.runId(),
                        WorkflowEventMessages.stageOperationFailed(
                                operationKind,
                                stageType,
                                attemptNumber,
                                attempt,
                                maxAttempts,
                                failureType,
                                summarizeEventEvidence(evidence)
                        ) + GenerationTelemetryFormatter.renderInline(telemetry)
                );
            }

            @Override
            public void onAttemptSucceeded(String operation, int attempt, int maxAttempts, GenerationTelemetry telemetry) {
                eventLogStore.append(
                        projectPath,
                        runRecord.runId(),
                        WorkflowEventMessages.stageOperationSucceeded(operationKind, stageType, attemptNumber, attempt, maxAttempts)
                                + GenerationTelemetryFormatter.renderInline(telemetry)
                );
            }
        };
    }

    private String summarizeEventEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return "";
        }
        String normalized = TextCanonicalizer.collapseWhitespace(evidence);
        return normalized.length() > 240 ? normalized.substring(0, 240) : normalized;
    }
}
