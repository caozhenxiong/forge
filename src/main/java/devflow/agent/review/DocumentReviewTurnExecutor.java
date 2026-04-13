package devflow.agent.review;

import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.loop.AgentTurnSnapshot;
import devflow.agent.loop.AgentTurnState;
import devflow.agent.loop.AgentTurnStepResult;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文档 review turn loop 执行器。
 *
 * <p>负责文档阶段的“准备上下文 -> 调 reviewer -> 观察结果 -> 归一化”内部状态机，
 * 让 `StageReviewer` 退化成阶段级门面。
 */
final class DocumentReviewTurnExecutor {

    private final LlmProvider llmProvider;
    private final AgentTurnLoop agentTurnLoop;
    private final DocumentReviewNormalizer documentReviewNormalizer;

    DocumentReviewTurnExecutor(
            LlmProvider llmProvider,
            AgentTurnLoop agentTurnLoop,
            DocumentReviewNormalizer documentReviewNormalizer
    ) {
        this.llmProvider = llmProvider;
        this.agentTurnLoop = agentTurnLoop;
        this.documentReviewNormalizer = documentReviewNormalizer;
    }

    ReviewResult run(
            RunRecord runRecord,
            StageType stageType,
            String sanitizedCandidate,
            String reviewerContext,
            String systemPrompt
    ) {
        AtomicReference<StructuredReviewResult> rawResultRef = new AtomicReference<>();
        AtomicReference<ReviewResult> normalizedRef = new AtomicReference<>();
        agentTurnLoop.runUntilSettled(
                AgentTurnSnapshot.start(),
                snapshot -> handleTurn(
                        snapshot,
                        runRecord,
                        stageType,
                        sanitizedCandidate,
                        reviewerContext,
                        systemPrompt,
                        rawResultRef,
                        normalizedRef
                )
        );
        return normalizedRef.get();
    }

    private AgentTurnStepResult handleTurn(
            AgentTurnSnapshot snapshot,
            RunRecord runRecord,
            StageType stageType,
            String sanitizedCandidate,
            String reviewerContext,
            String systemPrompt,
            AtomicReference<StructuredReviewResult> rawResultRef,
            AtomicReference<ReviewResult> normalizedRef
    ) {
        AgentTurnState state = snapshot.state();
        if (state == AgentTurnState.IDLE) {
            return AgentTurnStepResult.advance(
                    snapshot.next(AgentTurnState.PREPARE_CONTEXT, stageType.name(), "prepare-review-context")
            );
        }
        if (state == AgentTurnState.PREPARE_CONTEXT) {
            return AgentTurnStepResult.advance(
                    snapshot.next(AgentTurnState.EXECUTE_STEP, stageType.name(), "invoke-document-reviewer")
            );
        }
        if (state == AgentTurnState.EXECUTE_STEP) {
            rawResultRef.set(llmProvider.reviewStructured(
                    systemPrompt,
                    appendReviewerContext(sanitizedCandidate, reviewerContext),
                    java.util.Map.of(),
                    ModelRole.CODE_REVIEW
            ));
            return AgentTurnStepResult.advance(
                    snapshot.next(AgentTurnState.OBSERVE_RESULT, stageType.name(), "document-review-finished")
            );
        }
        if (state == AgentTurnState.OBSERVE_RESULT) {
            StructuredReviewResult raw = rawResultRef.get();
            normalizedRef.set(documentReviewNormalizer.normalize(
                    runRecord,
                    stageType,
                    sanitizedCandidate,
                    raw.result(),
                    raw.semantics()
            ));
            return AgentTurnStepResult.advance(
                    snapshot.next(AgentTurnState.EVALUATE_RESULT, stageType.name(), "normalize-document-review")
            );
        }
        if (state == AgentTurnState.EVALUATE_RESULT) {
            return AgentTurnStepResult.stop(
                    snapshot.next(AgentTurnState.COMPLETE, stageType.name(), "document-review-complete")
            );
        }
        return AgentTurnStepResult.stop(
                snapshot.next(AgentTurnState.FAILED, stageType.name(), "unexpected-review-state")
        );
    }

    private String appendReviewerContext(String candidate, String reviewerContext) {
        if (reviewerContext == null || reviewerContext.isBlank()) {
            return candidate;
        }
        return candidate + "\n\n## Reviewer Context\n\n" + reviewerContext.trim();
    }
}
