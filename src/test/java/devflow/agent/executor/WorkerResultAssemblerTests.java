package devflow.agent.executor;

import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerResultAssemblerTests {

    @Test
    void buildWorkerResultsAddsRunningResultForCurrentSubtask() {
        WorkerResultAssembler assembler = new WorkerResultAssembler();
        Subtask completedSubtask = new Subtask(
                "写入口",
                "创建最小入口",
                List.of("CAP-1"),
                List.of("入口"),
                List.of(),
                List.of("页面可打开"),
                true,
                DeliveryMode.SKELETON,
                List.of(new FileChange("index.html", ChangeAction.WRITE, "创建入口"))
        );
        Subtask runningSubtask = new Subtask(
                "补逻辑",
                "补核心逻辑",
                List.of("CAP-2"),
                List.of("逻辑"),
                List.of(),
                List.of("可以开始游戏"),
                false,
                DeliveryMode.INCREMENTAL,
                List.of(new FileChange("game.js", ChangeAction.WRITE, "补逻辑"))
        );
        ImplementationPlan plan = new ImplementationPlan("实现俄罗斯方块", List.of(completedSubtask, runningSubtask));
        SubtaskExecutionReport completedReport = new SubtaskExecutionReport(
                completedSubtask,
                true,
                List.of(SubtaskAttemptReport.fromVerification(
                        1,
                        new SelfCheckResult(true, "ok", ""),
                        new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "通过", "")
                ))
        );

        List<WorkerResult> results = assembler.buildWorkerResults(plan, List.of(completedReport), "补逻辑");

        assertEquals(2, results.size());
        assertEquals("COMPLETED", results.getFirst().status());
        assertEquals("RUNNING", results.get(1).status());
    }
}
