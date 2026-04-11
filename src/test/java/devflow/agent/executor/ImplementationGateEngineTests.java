package devflow.agent.executor;

import devflow.agent.context.ExecutionContract;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
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
}
