package devflow.agent.executor.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.DeliveryPolicyEnvelope;
import devflow.agent.executor.ImplementationExecutionBundle;
import devflow.agent.executor.ImplementationProgressSink;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.editing.RuntimeWorkingSetResolver;
import devflow.agent.executor.editing.TargetedFileContextRenderer;
import devflow.agent.executor.gate.ArchitectIntegrationCheck;
import devflow.agent.executor.gate.ImplementationGateEngine;
import devflow.agent.executor.gate.ImplementationStageGate;
import devflow.agent.executor.implementation.planning.ImplementationContinuationConstraints;
import devflow.agent.executor.implementation.planning.ImplementationPlan;
import devflow.agent.executor.implementation.planning.ImplementationPlanningWiring;
import devflow.agent.executor.implementation.state.ImplementationSnapshotAssembler;
import devflow.agent.executor.implementation.state.ReusableImplementationState;
import devflow.agent.executor.implementation.render.ImplementationArtifactRenderer;
import devflow.agent.executor.llm.ModelRole;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoderTurnCoordinatorTests {

    @TempDir
    Path tempDir;

    @Test
    void reusesPreviousStateFromExecutionContextWithoutReplanning() throws Exception {
        Path file = tempDir.resolve("src/app.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "export const value = 1;\n");

        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        FileArtifactStore fileArtifactStore = new FileArtifactStore(runRepository);
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        Subtask resumedSubtask = new Subtask(
                "修复 app",
                "沿用上一轮计划",
                List.of(),
                List.of(),
                List.of(),
                List.of("保留已验证计划"),
                false,
                DeliveryMode.PATCH,
                List.of(new FileChange("src/app.js", ChangeAction.WRITE, "复用旧计划"))
        );
        ReusableImplementationState reusableState = new ReusableImplementationState(
                new ImplementationPlan("resumed plan", List.of(resumedSubtask)),
                List.of(new SubtaskExecutionReport(resumedSubtask, true, List.of())),
                null
        );
        ImplementationResumePolicy resumePolicy = new ImplementationResumePolicy(new ObjectMapper()) {
            @Override
            public ReusableImplementationState loadReusableImplementationState(
                    String previousStateJson,
                    FixMode fixMode,
                    ImplementationPatchTarget implementationPatchTarget,
                    List<FileChange> overrideChanges,
                    DocumentLanguage language
            ) {
                assertEquals("previous-state", previousStateJson);
                assertEquals(FixMode.PATCH, fixMode);
                return reusableState;
            }
        };
        CoderTurnCoordinator coordinator = new CoderTurnCoordinator(
                eventLogStore,
                fileArtifactStore,
                new ImplementationStageGate(),
                new ImplementationGateEngine(
                        new ImplementationStageGate(),
                        new ArchitectIntegrationCheck(workspace, new TreeSitterSupport())
                ),
                resumePolicy,
                ImplementationPlanningWiring.createPlanner(
                        new devflow.agent.testsupport.RequestBackedLlmProvider() {
                            @Override
                            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                                throw new AssertionError("planner should not run when reusable state exists");
                            }

                            @Override
                            public String generate(
                                    String systemPrompt,
                                    String userPrompt,
                                    Map<String, Object> options,
                                    ModelRole role
                            ) {
                                throw new AssertionError("planner should not run when reusable state exists");
                            }
                        },
                        workspace,
                        new ObjectMapper(),
                        new devflow.agent.executor.implementation.planning.ImplementationPlanCoverageAnalyzer(),
                        new devflow.agent.loop.AgentTurnLoop(),
                        2,
                        3,
                        2,
                        3
                ),
                new ImplementationPlanRunner(null),
                new ImplementationSnapshotAssembler(
                        new ImplementationArtifactRenderer(new ObjectMapper()),
                        new TargetedFileContextRenderer(workspace, new RuntimeWorkingSetResolver())
                )
        );

        ImplementationExecutionBundle bundle = coordinator.execute(
                tempDir,
                runRecord(),
                "修复上一轮失败点",
                new ImplementationExecutionContext(
                        DocumentLanguage.ZH,
                        "",
                        "",
                        "",
                        "",
                        new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 2, 4, true, false, true, List.of()),
                        FixMode.PATCH,
                        ImplementationPatchTarget.NONE,
                        List.of(),
                        null,
                        null,
                        QualityPlan.empty(),
                        false,
                        new SharedContextBundle("", "", null, List.of(), List.of(), List.of(), "", ""),
                        "",
                        ImplementationContinuationConstraints.empty(),
                        "previous-state"
                ),
                ImplementationProgressSink.noop()
        );

        assertEquals("resumed plan", bundle.snapshot().plan().summary());
        assertEquals(1, bundle.snapshot().reports().size());
        assertTrue(bundle.snapshot().reports().get(0).completed());
        assertTrue(bundle.eventsMarkdown().contains("实现阶段｜复用旧计划"));
    }

    private RunRecord runRecord() {
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        stageStates.put(StageType.IMPLEMENTATION, new StageExecution(
                StageType.IMPLEMENTATION,
                StageStatus.RUNNING,
                1,
                null,
                null,
                null,
                null
        ));
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "修复现有实现",
                "继续当前计划",
                RunConfig.defaultConfig(),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }
}
