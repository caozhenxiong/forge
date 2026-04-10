package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationArtifactRendererTests {

    @Test
    void renderersKeepSingleSnapshotViewsConsistent() {
        ImplementationArtifactRenderer renderer = new ImplementationArtifactRenderer(new ObjectMapper());
        Subtask subtask = new Subtask(
                "补逻辑",
                "补核心逻辑",
                List.of("CAP-1"),
                List.of("逻辑"),
                List.of(),
                List.of("可以开始游戏"),
                false,
                DeliveryMode.INCREMENTAL,
                List.of(new FileChange("game.js", ChangeAction.WRITE, "补逻辑", FileEditScope.AUTO))
        );
        ImplementationPlan plan = new ImplementationPlan("实现俄罗斯方块", List.of(subtask));
        SubtaskExecutionReport report = new SubtaskExecutionReport(
                subtask,
                false,
                List.of(new SubtaskAttemptReport(
                        1,
                        new SelfCheckResult(true, "ok", ""),
                        new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "需要补逻辑", "请补齐掉落逻辑"),
                        new GenerationFailureReport(
                                "game.js",
                                DeliveryMode.INCREMENTAL.name(),
                                "precise-code",
                                GenerationFailureType.OUTPUT_TRUNCATED,
                                1,
                                true,
                                "输出被截断",
                                "done_reason=length",
                                "请缩小 patch"
                        ),
                        null
                ))
        );
        ImplementationRuntimeSnapshot snapshot = new ImplementationRuntimeSnapshot(
                plan,
                List.of(),
                List.of(report),
                List.of(new WorkerResult(
                        "补逻辑",
                        "FAILED",
                        false,
                        List.of("game.js"),
                        List.of("CAP-1"),
                        List.of("逻辑"),
                        List.of(),
                        List.of(),
                        List.of("请补齐掉落逻辑"),
                        "ok",
                        "需要补逻辑",
                        "请补齐掉落逻辑"
                )),
                List.of(new ImplementationEventEntry(Instant.parse("2026-04-09T00:00:00Z"), "实现阶段｜子任务失败｜标题=补逻辑")),
                "repair_brief_enforced=false",
                DocumentLanguage.ZH,
                new DeliveryPolicyEnvelope(DeliveryMode.INCREMENTAL, 2, 4, true, false, true, List.of()),
                null,
                new ImplementationStageStatus(1, 1, 0, false, false, false, List.of("补逻辑")),
                "补逻辑"
        );

        assertTrue(renderer.renderReport(snapshot).contains("子任务执行结果"));
        assertTrue(renderer.renderProgress(snapshot).contains("RUNNING") || renderer.renderProgress(snapshot).contains("FAILED"));
        assertTrue(renderer.renderWorkerResults(snapshot).contains("执行结果"));
        assertTrue(renderer.renderEvents(snapshot).contains("实现阶段｜子任务失败"));
        assertTrue(renderer.renderStateJson(snapshot).contains("\"generationFailure\""));
    }
}
