package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.toolloop.*;

import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskAttemptReport;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
import devflow.agent.executor.subtask.SubtaskExecutionState;
class ImplementationStageGateTests {

    @Test
    void generatesGenericContinuationForIncompletePlanWithoutReviewDirective() {
        ImplementationStageGate gate = new ImplementationStageGate();
        Subtask first = subtask("搭入口", true, "index.html");
        Subtask second = subtask("补逻辑", false, "game.js");

        ImplementationStageStatus stageStatus = gate.summarizeStageStatus(
                new ImplementationPlan("summary", List.of(first, second)),
                List.of(completedReport(first)),
                null
        );

        assertFalse(stageStatus.stageReady());
        assertEquals(ImplementationContinuationMode.CONTINUE_SUBTASKS, stageStatus.continuationMode());
        assertEquals("实现计划尚未执行完毕，当前仍处于阶段中间态。", stageStatus.continuationSummary());
        assertTrue(stageStatus.continuationEvidence().contains("补逻辑"));
    }

    @Test
    void generatesRuntimeWiringContinuationFromContractGate() {
        ImplementationStageGate gate = new ImplementationStageGate();
        Subtask subtask = subtask("修接线", false, "index.html");
        HtmlRuntimeOwnershipContract runtimeContract = HtmlRuntimeOwnershipContract.externalCompanion(
                Path.of("index.html"),
                List.of(Path.of("index.app.js"))
        );

        ImplementationStageStatus stageStatus = gate.summarizeStageStatus(
                new ImplementationPlan("summary", List.of(subtask)),
                List.of(completedReport(subtask)),
                ArchitectIntegrationCheckResult.failure(
                        ArchitectIntegrationCheckScope.STAGE_COMPLETION,
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID,
                        "index.app.js exists but index.html does not reference it",
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                        runtimeContract
                )
        );

        assertEquals(ImplementationContinuationMode.CONTINUE_SUBTASKS, stageStatus.continuationMode());
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING, stageStatus.continuationPatchTarget());
        assertEquals(ReviewReasonCode.RUNTIME_WIRING_GAP, stageStatus.continuationReasonCode());
        assertEquals(2, stageStatus.continuationOverrideChanges().size());
        assertEquals("index.html", stageStatus.continuationOverrideChanges().getFirst().path());
        assertEquals("index.app.js", stageStatus.continuationOverrideChanges().get(1).path());
    }

    @Test
    void blocksWhenContractGateCannotProduceStructuredImplementationScope() {
        ImplementationStageGate gate = new ImplementationStageGate();
        Subtask subtask = subtask("补交互", false, "index.html");

        ImplementationStageStatus stageStatus = gate.summarizeStageStatus(
                new ImplementationPlan("summary", List.of(subtask)),
                List.of(completedReport(subtask)),
                ArchitectIntegrationCheckResult.failure(
                        ArchitectIntegrationCheckScope.STAGE_COMPLETION,
                        ArchitectIntegrationFailureReason.IMPLEMENTATION_INCOMPLETE,
                        "missing interaction feedback",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                )
        );

        assertEquals(ImplementationContinuationMode.BLOCK_STAGE, stageStatus.continuationMode());
        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION, stageStatus.continuationPatchTarget());
        assertEquals(ReviewReasonCode.IMPLEMENTATION_GAP, stageStatus.continuationReasonCode());
        assertTrue(stageStatus.continuationSummary().contains("结构化文件范围"));
        assertTrue(stageStatus.continuationOverrideChanges().isEmpty());
    }

    @Test
    void derivesContinuationScopeFromFailedSubtaskEffectiveChanges() {
        ImplementationStageGate gate = new ImplementationStageGate();
        Subtask subtask = new Subtask(
                "补逻辑",
                "补逻辑",
                List.of(),
                List.of(),
                List.of(),
                List.of("完成当前子任务"),
                false,
                DeliveryMode.PATCH,
                List.of(
                        new FileChange("src/game.js", ChangeAction.WRITE, "补齐核心逻辑"),
                        new FileChange("README.md", ChangeAction.WRITE, "不应进入 repair scope")
                )
        );
        SubtaskExecutionState executionState = new SubtaskExecutionState(DeliveryMode.PATCH, true);
        executionState.setEffectiveChanges(List.of(new FileChange("src/game.js", ChangeAction.WRITE, "补齐核心逻辑")));
        ReviewResult patchReview = new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                "需要继续修补",
                "继续修复当前子任务",
                "tool loop exceeded max turns without a terminal assistant response",
                "继续修复",
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                List.of()
        );

        ImplementationStageStatus stageStatus = gate.summarizeStageStatus(
                new ImplementationPlan("summary", List.of(subtask)),
                List.of(new SubtaskExecutionReport(
                        subtask,
                        false,
                        List.of(SubtaskAttemptReport.fromVerification(
                                1,
                                new SelfCheckResult(false, "failed", ""),
                                List.of(),
                                patchReview
                        )),
                        executionState
                )),
                null
        );

        assertEquals(ImplementationContinuationMode.CONTINUE_SUBTASKS, stageStatus.continuationMode());
        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION, stageStatus.continuationPatchTarget());
        assertEquals(1, stageStatus.continuationOverrideChanges().size());
        assertEquals("src/game.js", stageStatus.continuationOverrideChanges().getFirst().path());
    }

    @Test
    void clampsPatchScopeToCurrentSubtaskEffectiveChanges() {
        ImplementationStageGate gate = new ImplementationStageGate();
        Subtask subtask = new Subtask(
                "补逻辑",
                "补逻辑",
                List.of(),
                List.of(),
                List.of(),
                List.of("完成当前子任务"),
                false,
                DeliveryMode.PATCH,
                List.of(new FileChange(
                        "index.html",
                        ChangeAction.WRITE,
                        "补齐核心逻辑",
                        FileEditScope.HOST_HTML_PATCH,
                        RuntimeOwnershipMode.EXTERNAL_COMPANION,
                        true
                ))
        );
        SubtaskExecutionState executionState = new SubtaskExecutionState(DeliveryMode.PATCH, true);
        executionState.setEffectiveChanges(List.of(new FileChange(
                "index.html",
                ChangeAction.WRITE,
                "补齐核心逻辑",
                FileEditScope.HOST_HTML_PATCH,
                RuntimeOwnershipMode.EXTERNAL_COMPANION,
                true
        )));
        ReviewResult patchReview = new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                "需要继续修补",
                "继续修复当前子任务",
                "evidence",
                "继续修复",
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                List.of(
                        new FileChange("index.html", ChangeAction.DELETE, "篡改 metadata"),
                        new FileChange("STATUS.txt", ChangeAction.WRITE, "越界路径")
                )
        );

        ImplementationStageStatus stageStatus = gate.summarizeStageStatus(
                new ImplementationPlan("summary", List.of(subtask)),
                List.of(new SubtaskExecutionReport(
                        subtask,
                        false,
                        List.of(SubtaskAttemptReport.fromVerification(
                                1,
                                new SelfCheckResult(false, "failed", ""),
                                List.of(),
                                patchReview
                        )),
                        executionState
                )),
                null
        );

        assertEquals(1, stageStatus.continuationOverrideChanges().size());
        assertEquals("index.html", stageStatus.continuationOverrideChanges().getFirst().path());
        assertEquals(ChangeAction.WRITE, stageStatus.continuationOverrideChanges().getFirst().action());
        assertEquals(FileEditScope.HOST_HTML_PATCH, stageStatus.continuationOverrideChanges().getFirst().editScope());
        assertEquals(RuntimeOwnershipMode.EXTERNAL_COMPANION,
                stageStatus.continuationOverrideChanges().getFirst().runtimeOwnership());
        assertTrue(stageStatus.continuationOverrideChanges().getFirst().hostHtmlPatchRequired());
    }

    private Subtask subtask(String title, boolean runnableMilestone, String path) {
        return new Subtask(
                title,
                title,
                List.of(),
                List.of(),
                List.of(),
                List.of("完成当前子任务"),
                runnableMilestone,
                DeliveryMode.PATCH,
                List.of(new FileChange(path, ChangeAction.WRITE, "test"))
        );
    }

    private SubtaskExecutionReport completedReport(Subtask subtask) {
        return new SubtaskExecutionReport(
                subtask,
                true,
                List.of(SubtaskAttemptReport.fromVerification(
                        1,
                        new SelfCheckResult(true, "ok", ""),
                        List.of(),
                        new devflow.agent.review.ReviewResult(
                                devflow.agent.review.ReviewDecision.APPROVED,
                                devflow.agent.review.FixMode.NONE,
                                "ok",
                                ""
                        )
                )),
                null
        );
    }
}
