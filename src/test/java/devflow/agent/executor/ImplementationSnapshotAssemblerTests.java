package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationSnapshotAssemblerTests {

    @Test
    void buildSnapshotAddsRunningWorkerResultForCurrentSubtask() {
        ImplementationSnapshotAssembler assembler = new ImplementationSnapshotAssembler(
                new ImplementationArtifactRenderer(new ObjectMapper()),
                null
        );
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
                        List.of(),
                        new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "通过", "")
                ))
        );

        ImplementationRuntimeSnapshot snapshot = assembler.buildSnapshot(
                plan,
                List.of(completedReport),
                List.of(new ImplementationEventEntry(Instant.parse("2026-04-09T00:00:00Z"), "实现阶段｜子任务开始｜标题=补逻辑")),
                "",
                DocumentLanguage.ZH,
                new DeliveryPolicyEnvelope(DeliveryMode.INCREMENTAL, 2, 4, true, false, true, List.of()),
                null,
                List.of(),
                new ImplementationStageStatus(2, 1, 1, false, false, List.of("补逻辑")),
                "补逻辑"
        );

        assertEquals(2, snapshot.workerResults().size());
        assertEquals("RUNNING", snapshot.workerResults().get(1).status());

        ImplementationExecutionBundle bundle = assembler.buildBundle(snapshot);
        assertTrue(bundle.workerResultsMarkdown().contains("执行结果: 补逻辑"));
        assertTrue(bundle.workerResultsMarkdown().contains("状态: RUNNING") || bundle.workerResultsMarkdown().contains("status: RUNNING"));
        assertTrue(bundle.eventsMarkdown().contains("实现阶段｜子任务开始｜标题=补逻辑"));
        assertTrue(bundle.stateJson().contains("\"events\""));
        assertTrue(bundle.stateJson().contains("实现阶段｜子任务开始｜标题=补逻辑"));
    }
}
