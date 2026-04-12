package devflow.agent.executor;

import devflow.agent.context.ExecutionContract;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationGateEngineTests {

    @TempDir
    Path tempDir;

    @Test
    void returnsCurrentStageStatusWhenPlanIsIncomplete() {
        ImplementationGateEngine gateEngine = new ImplementationGateEngine(
                new ImplementationStageGate(),
                new ArchitectIntegrationCheck(new FileProjectWorkspace(), new TreeSitterSupport())
        );

        Subtask first = subtask("搭入口", true, "index.html");
        Subtask second = subtask("补逻辑", false, "game.js");
        ImplementationPlan plan = new ImplementationPlan("summary", List.of(first, second));
        List<SubtaskExecutionReport> reports = List.of(completedReport(first));

        ImplementationGateOutcome outcome = gateEngine.evaluate(
                tempDir,
                plan,
                reports,
                new ExecutionContract(true, "html-entry", true, true, List.of()),
                DocumentLanguage.ZH
        );

        assertEquals(reports, outcome.reports());
        assertFalse(outcome.stageStatus().planCompleted());
        assertFalse(outcome.stageStatus().stageReady());
        assertEquals(List.of("补逻辑"), outcome.stageStatus().incompleteSubtasks());
    }

    @Test
    void appendsArchitectFailureWhenCompletedPlanStillIsNotRunnable() {
        ImplementationGateEngine gateEngine = new ImplementationGateEngine(
                new ImplementationStageGate(),
                new ArchitectIntegrationCheck(new FileProjectWorkspace(), new TreeSitterSupport())
        );

        Subtask subtask = subtask("搭入口", true, "index.html");
        ImplementationPlan plan = new ImplementationPlan("summary", List.of(subtask));
        List<SubtaskExecutionReport> reports = List.of(completedReport(subtask));

        ImplementationGateOutcome outcome = gateEngine.evaluate(
                tempDir,
                plan,
                reports,
                new ExecutionContract(true, "html-entry", true, true, List.of()),
                DocumentLanguage.ZH
        );

        assertNotNull(outcome.architectCheckResult());
        assertFalse(outcome.architectCheckResult().passed());
        assertEquals(2, outcome.reports().size());
        assertFalse(outcome.stageStatus().architectCheckPassed());
        assertFalse(outcome.stageStatus().stageReady());
        assertTrue(outcome.reports().get(1).subtask().title().contains("架构师整体可运行检查"));
    }

    @Test
    void preservesStructuredContinuationWhenLatestFailedSubtaskRequiresRuntimePatch() {
        ImplementationGateEngine gateEngine = new ImplementationGateEngine(
                new ImplementationStageGate(),
                new ArchitectIntegrationCheck(new FileProjectWorkspace(), new TreeSitterSupport())
        );

        Subtask first = subtask("搭入口", true, "index.html");
        Subtask second = subtask("修接线", false, "index.html");
        ImplementationPlan plan = new ImplementationPlan("summary", List.of(first, second));
        List<SubtaskExecutionReport> reports = List.of(
                completedReport(first),
                failedReport(
                        second,
                        new ReviewResult(
                                ReviewDecision.REVISION_REQUIRED,
                                FixMode.PATCH,
                                "运行时接线未完成",
                                "把 companion runtime 接入宿主 HTML。",
                                "index.app.js exists but index.html does not reference it",
                                "1. 仅修复当前入口接线。 2. 保持当前实现结构不变。",
                                ImplementationPatchTarget.PATCH_RUNTIME_WIRING
                        )
                )
        );

        ImplementationGateOutcome outcome = gateEngine.evaluate(
                tempDir,
                plan,
                reports,
                new ExecutionContract(true, "html-entry", true, true, List.of()),
                DocumentLanguage.ZH
        );

        assertFalse(outcome.stageStatus().planCompleted());
        assertFalse(outcome.stageStatus().stageReady());
        assertEquals(ImplementationContinuationMode.CONTINUE_SUBTASKS, outcome.stageStatus().continuationMode());
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING, outcome.stageStatus().continuationPatchTarget());
        assertEquals("运行时接线未完成", outcome.stageStatus().continuationSummary());
        assertTrue(outcome.stageStatus().continuationEvidence().contains("continuationSubtask=修接线"));
        assertTrue(outcome.stageStatus().hasContinuationDirective());
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
                ))
        );
    }

    private SubtaskExecutionReport failedReport(Subtask subtask, ReviewResult reviewResult) {
        return new SubtaskExecutionReport(
                subtask,
                false,
                List.of(SubtaskAttemptReport.fromVerification(
                        1,
                        new SelfCheckResult(false, "failed", "details"),
                        List.of(),
                        reviewResult
                ))
        );
    }
}
