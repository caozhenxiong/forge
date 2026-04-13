package devflow.agent.orchestrator;

import devflow.agent.executor.generation.GenerationAttemptResult;
import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.generation.GenerationFailureException;
import devflow.agent.executor.generation.GenerationFailureExceptions;
import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.generation.GenerationSpec;
import devflow.agent.executor.llm.LlmFailureReason;
import devflow.agent.executor.llm.LlmInvocationException;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageType;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.StageArtifactComposer;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.StageReviewer;
import java.nio.file.Path;

/**
 * 统一包装阶段产物生成和阶段审阅的“可观测长调用”。
 * 这样 workflow engine 不需要继续自己拼 generation/review 的 heartbeat、timeout 和失败收尾。
 */
public class StageOperationExecutor {

    private final StageArtifactComposer stageArtifactComposer;
    private final StageReviewer stageReviewer;
    private final GenerationEngine executionEngine;
    private final StageOperationPolicy stageOperationPolicy;
    private final StageOperationObserverFactory observerFactory;

    public StageOperationExecutor(
            StageArtifactComposer stageArtifactComposer,
            StageReviewer stageReviewer,
            EventLogStore eventLogStore,
            GenerationEngine executionEngine,
            StageOperationPolicy stageOperationPolicy
    ) {
        this.stageArtifactComposer = stageArtifactComposer;
        this.stageReviewer = stageReviewer;
        this.executionEngine = executionEngine;
        this.stageOperationPolicy = stageOperationPolicy;
        this.observerFactory = new StageOperationObserverFactory(eventLogStore);
    }

    public String composeStageArtifact(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            StageExecution stageExecution,
            String note
    ) {
        return executionEngine.execute(new GenerationSpec<>(
                "stage-generate:" + stageType,
                1,
                stageOperationPolicy.generationHeartbeatInterval(stageType),
                stageOperationPolicy.generationTimeout(stageType),
                observerFactory.generationObserver(projectPath, runRecord, stageType, stageExecution.attempt()),
                (attempt, retryFeedback) -> GenerationAttemptResult.success(
                        stageArtifactComposer.compose(projectPath, runRecord, stageType, note)
                ),
                this::mapInvocationFailure,
                    (failureType, evidence) -> GenerationFailureExceptions.create(
                            stageType.name(),
                            "generation",
                            "stage-generate",
                            failureType,
                            1,
                            false,
                            "阶段产物生成失败",
                            evidence,
                            "请保留当前阶段上下文并重新生成阶段产物。"
                    ),
                stageArtifactComposer::consumeLastTelemetry
        ));
    }

    /**
     * 阶段 review 失败时，当前策略是保留产物并请求对当前阶段做 patch，而不是直接推进流程。
     */
    public ReviewResult reviewStage(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            StageExecution stageExecution,
            String artifactContent,
            DocumentLanguage language
    ) {
        try {
            return executionEngine.execute(new GenerationSpec<>(
                    "stage-review:" + stageType,
                    1,
                    stageOperationPolicy.reviewHeartbeatInterval(stageType),
                    stageOperationPolicy.reviewTimeout(stageType),
                    observerFactory.reviewObserver(projectPath, runRecord, stageType, stageExecution.attempt()),
                    (attempt, retryFeedback) -> GenerationAttemptResult.success(
                            stageReviewer.review(projectPath, runRecord, stageType, artifactContent)
                    ),
                    this::mapInvocationFailure,
                    (failureType, evidence) -> GenerationFailureExceptions.create(
                            stageType.name(),
                            "review",
                            "stage-review",
                            failureType,
                            1,
                            false,
                            "阶段审阅调用失败",
                            evidence,
                            "请保留当前阶段产物并重新执行阶段审阅。"
                    ),
                    stageReviewer::consumeLastTelemetry
            ));
        } catch (GenerationFailureException exception) {
            return new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                    language.choose("阶段审阅调用超时或失败，暂不放行当前阶段。", "Stage review timed out or failed, so the stage cannot be approved yet."),
                    language.choose("请保留当前阶段产物，重新执行当前阶段审阅。", "Keep the current stage artifact and rerun the current stage review."),
                    exception.report().evidence(),
                    language.choose("先重新执行当前阶段审阅；若仍多次超时，再缩小 review 输入。", "Retry the current stage review first; if it still times out repeatedly, shrink the review input.")
            );
        }
    }

    private GenerationFailureType mapInvocationFailure(Exception exception) {
        if (exception instanceof LlmInvocationException llmInvocationException
                && llmInvocationException.reason() == LlmFailureReason.TIMEOUT) {
            return GenerationFailureType.ATTEMPT_TIMEOUT;
        }
        return GenerationFailureType.MODEL_INVOCATION_FAILED;
    }
}
