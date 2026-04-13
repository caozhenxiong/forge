package devflow.agent.context;

import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextLayerAssemblerTests {

    private final ContextLayerAssembler assembler = new ContextLayerAssembler();

    @Test
    void assemblesFourContextLayersFromProjectedInputs() {
        RunRecord runRecord = dummyRun();
        ContractView contractView = new ContractView(null, null, new ExecutionContract(true, "html-entry", true, true, List.of("page-opens")), ConstraintSourceMetadata.empty());
        List<FailureDigest> failures = List.of(new FailureDigest(StageType.IMPLEMENTATION, "失败摘要", "修复入口接线", "", ""));

        ContextViews views = assembler.assemble(
                runRecord,
                StageType.IMPLEMENTATION,
                contractView,
                "当前在实现阶段补入口接线",
                "上游要求提供可直接运行入口",
                "- CAP-1 [required-capability, planning-required]: 提供可运行入口",
                "ANALYSIS -> PRD -> DESIGN 均已通过",
                "最近一次 implementation 失败在入口接线",
                "优先修入口接线",
                "当前 working set 为 index.html + game.js",
                failures
        );

        assertNotNull(views.durableContext());
        assertNotNull(views.workingContext());
        assertNotNull(views.evidenceContext());
        assertNotNull(views.traceContext());
        assertEquals("实现可直接运行的俄罗斯方块网页小游戏", views.durableContext().goal());
        assertTrue(views.durableContext().authoritativeRequirementCatalog().contains("CAP-1"));
        assertEquals(StageType.IMPLEMENTATION, views.workingContext().currentStage());
        assertEquals("最近一次 implementation 失败在入口接线", views.evidenceContext().failureSummary());
        assertEquals("ANALYSIS -> PRD -> DESIGN 均已通过", views.traceContext().recentHistorySummary());

        ContextSlice plannerSlice = views.forProfile(ContextAccessProfile.PLANNER);
        assertNotNull(plannerSlice.durableContext());
        assertNull(plannerSlice.workingContext());
        assertNotNull(plannerSlice.evidenceContext());
        assertNull(plannerSlice.traceContext());

        ContextSlice coderSlice = views.forProfile(ContextAccessProfile.CODER);
        assertNotNull(coderSlice.durableContext());
        assertNotNull(coderSlice.workingContext());
        assertNotNull(coderSlice.evidenceContext());
        assertNull(coderSlice.traceContext());

        ContextSlice flowSlice = views.forProfile(ContextAccessProfile.FLOW_CONTROLLER);
        assertNull(flowSlice.durableContext());
        assertNull(flowSlice.workingContext());
        assertNotNull(flowSlice.evidenceContext());
        assertNotNull(flowSlice.traceContext());
    }

    private RunRecord dummyRun() {
        Map<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                Path.of("/tmp/project"),
                "实现可直接运行的俄罗斯方块网页小游戏",
                "纯前端运行",
                RunConfig.defaultConfig(),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }
}
