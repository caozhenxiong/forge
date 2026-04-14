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

import devflow.agent.executor.generation.GenerationFailureReport;
import devflow.agent.executor.generation.GenerationFailureType;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import devflow.agent.executor.implementation.ImplementationEventEntry;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskAttemptReport;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
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
                        List.of(),
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
                new ImplementationStageStatus(1, 1, 0, false, false, List.of("补逻辑")),
                "补逻辑"
        );

        assertTrue(renderer.renderReport(snapshot).contains("子任务执行结果"));
        assertTrue(renderer.renderProgress(snapshot).contains("RUNNING") || renderer.renderProgress(snapshot).contains("FAILED"));
        assertTrue(renderer.renderWorkerResults(snapshot).contains("执行结果"));
        assertTrue(renderer.renderEvents(snapshot).contains("实现阶段｜子任务失败"));
        assertTrue(renderer.renderStateJson(snapshot).contains("\"generationFailure\""));
    }

    @Test
    void renderersExposeStructuredContinuationForIncompleteStage() {
        ImplementationArtifactRenderer renderer = new ImplementationArtifactRenderer(new ObjectMapper());
        Subtask subtask = new Subtask(
                "修接线",
                "修入口接线",
                List.of("CAP-1"),
                List.of("接线"),
                List.of(),
                List.of("宿主 HTML 接入 companion runtime"),
                false,
                DeliveryMode.PATCH,
                List.of(new FileChange("index.html", ChangeAction.WRITE, "修入口接线", FileEditScope.AUTO))
        );
        ImplementationPlan plan = new ImplementationPlan("修接线", List.of(subtask));
        ImplementationStageStatus stageStatus = new ImplementationStageStatus(
                1,
                1,
                0,
                false,
                false,
                List.of("修接线"),
                null,
                devflow.agent.protocol.ImplementationContinuationMode.CONTINUE_SUBTASKS,
                "继续修当前入口接线",
                "只修宿主 HTML 与 companion runtime 的接线。",
                "continuationSubtask=修接线\nindex.app.js exists but index.html does not reference it",
                "1. 引入 companion runtime。 2. 保持当前实现结构。",
                List.of(
                        new FileChange(
                                "index.html",
                                ChangeAction.WRITE,
                                "修入口接线",
                                FileEditScope.HOST_HTML_PATCH,
                                RuntimeOwnershipMode.EXTERNAL_COMPANION,
                                true
                        ),
                        new FileChange("index.app.js", ChangeAction.WRITE, "对齐 companion runtime")
                ),
                ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                ReviewReasonCode.NONE
        );
        ImplementationRuntimeSnapshot snapshot = new ImplementationRuntimeSnapshot(
                plan,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                DocumentLanguage.ZH,
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 2, 4, true, false, true, List.of()),
                null,
                stageStatus,
                null
        );

        assertTrue(renderer.renderReport(snapshot).contains("continuationPatchTarget: PATCH_RUNTIME_WIRING"));
        assertTrue(renderer.renderReport(snapshot).contains("continuationChangeRequest: 只修宿主 HTML 与 companion runtime 的接线。"));
        assertTrue(renderer.renderReport(snapshot).contains("continuationOverrideChanges:"));
        assertTrue(renderer.renderProgress(snapshot).contains("continuationPatchTarget: PATCH_RUNTIME_WIRING"));
        assertTrue(renderer.renderProgress(snapshot).contains("continuationSummary: 继续修当前入口接线"));
        assertTrue(renderer.renderProgress(snapshot).contains("continuationOverrideChanges:"));
    }
}
