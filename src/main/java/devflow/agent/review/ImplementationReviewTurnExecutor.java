package devflow.agent.review;

import devflow.agent.executor.GenerationBudgetProfile;
import devflow.agent.executor.LlmOptions;
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
 * implementation review turn loop 执行器。
 *
 * <p>负责实现阶段 review 的模型调用和结果归一化状态机，
 * 让 `StageReviewer` 不再直接维护第二套 turn loop。
 */
final class ImplementationReviewTurnExecutor {

    private static final String SYSTEM_PROMPT = """
            你是严格的软件工程评审，请判断这次实现是否真正落地、是否有明显遗漏或危险改动。
            若实现报告中的子任务状态与实际代码变更、自检结果冲突，以实际代码和自检结果为准，不要因为报告里旧的 failed 标记而直接拒绝。
            约束：
            1. 只能基于输入中的明确证据下结论，不要脑补不存在的问题。
            2. 先检查功能正确性、结构一致性、入口接线和现有代码中的确定性缺陷，再考虑性能风险。
            3. 如果要给出“性能不达标”“超过 500ms”“速度太慢”这类结论，必须引用自检结果或测试结果中的明确测量证据。
            4. 如果没有测量数据，只能写“存在性能风险”或“缺少性能验证”，不能直接判定未达验收指标。
            5. changeRequest 必须具体到文件、函数、模块、测试或验证动作，不能只给空泛重构建议。
            6. actionItems 必须是 coder 可以直接执行的分步动作。
            7. 只有在结构明显错误、重复实现、入口未接线、模块边界混乱时才使用 REWORK；其余优先 PATCH。
            8. 如果输入里带有 Repair Brief 或 Repair Alignment，必须把它们视为当前轮修复契约，逐项检查 must-fix-first、forbidden directions 和 acceptance checks 是否满足。
            9. 如果 Repair Brief 明确要求的关键修复项仍未落实，不要批准当前实现。
            """;

    private final LlmProvider llmProvider;
    private final AgentTurnLoop agentTurnLoop;
    private final ImplementationReviewNormalizer implementationReviewNormalizer;
    private final ReviewArtifactLoader reviewArtifactLoader;
    private final ImplementationReviewContextAssembler contextAssembler;

    ImplementationReviewTurnExecutor(
            LlmProvider llmProvider,
            AgentTurnLoop agentTurnLoop,
            ImplementationReviewNormalizer implementationReviewNormalizer,
            ReviewArtifactLoader reviewArtifactLoader
    ) {
        this.llmProvider = llmProvider;
        this.agentTurnLoop = agentTurnLoop;
        this.implementationReviewNormalizer = implementationReviewNormalizer;
        this.reviewArtifactLoader = reviewArtifactLoader;
        this.contextAssembler = new ImplementationReviewContextAssembler(reviewArtifactLoader);
    }

    ReviewResult run(RunRecord runRecord, String candidate, String reviewerContext) {
        AtomicReference<StructuredReviewResult> rawResultRef = new AtomicReference<>();
        AtomicReference<ReviewResult> normalizedRef = new AtomicReference<>();
        agentTurnLoop.runUntilSettled(
                AgentTurnSnapshot.start(),
                snapshot -> handleTurn(snapshot, runRecord, candidate, reviewerContext, rawResultRef, normalizedRef)
        );
        return normalizedRef.get();
    }

    private AgentTurnStepResult handleTurn(
            AgentTurnSnapshot snapshot,
            RunRecord runRecord,
            String candidate,
            String reviewerContext,
            AtomicReference<StructuredReviewResult> rawResultRef,
            AtomicReference<ReviewResult> normalizedRef
    ) {
        AgentTurnState state = snapshot.state();
        if (state == AgentTurnState.IDLE) {
            return AgentTurnStepResult.advance(
                    snapshot.next(AgentTurnState.PREPARE_CONTEXT, StageType.IMPLEMENTATION.name(), "prepare-implementation-review")
            );
        }
        if (state == AgentTurnState.PREPARE_CONTEXT) {
            return AgentTurnStepResult.advance(
                    snapshot.next(AgentTurnState.EXECUTE_STEP, StageType.IMPLEMENTATION.name(), "invoke-implementation-reviewer")
            );
        }
        if (state == AgentTurnState.EXECUTE_STEP) {
            rawResultRef.set(llmProvider.reviewStructured(
                    SYSTEM_PROMPT,
                    contextAssembler.assemble(runRecord, candidate, reviewerContext),
                    LlmOptions.outputBudgetRatio(GenerationBudgetProfile.implementationReviewOutputRatio()),
                    ModelRole.CODE_REVIEW
            ));
            return AgentTurnStepResult.advance(
                    snapshot.next(AgentTurnState.OBSERVE_RESULT, StageType.IMPLEMENTATION.name(), "implementation-review-finished")
            );
        }
        if (state == AgentTurnState.OBSERVE_RESULT) {
            StructuredReviewResult raw = rawResultRef.get();
            normalizedRef.set(implementationReviewNormalizer.normalize(
                    raw.result(),
                    reviewArtifactLoader.readStageArtifact(runRecord, StageType.DESIGN),
                    raw.semantics()
            ));
            return AgentTurnStepResult.advance(
                    snapshot.next(AgentTurnState.EVALUATE_RESULT, StageType.IMPLEMENTATION.name(), "normalize-implementation-review")
            );
        }
        if (state == AgentTurnState.EVALUATE_RESULT) {
            return AgentTurnStepResult.stop(
                    snapshot.next(AgentTurnState.COMPLETE, StageType.IMPLEMENTATION.name(), "implementation-review-complete")
            );
        }
        return AgentTurnStepResult.stop(
                snapshot.next(AgentTurnState.FAILED, StageType.IMPLEMENTATION.name(), "unexpected-review-state")
        );
    }

}
