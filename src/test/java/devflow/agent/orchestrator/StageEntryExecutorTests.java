package devflow.agent.orchestrator;

import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.llm.LlmProvider;

import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageEntryExecutorTests {

    @TempDir
    Path tempDir;

    @Test
    void enterStageWritesDirectiveSidecarWithoutPollutingCanonicalArtifact() throws Exception {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        EventLogStore eventLogStore = new EventLogStore(runRepository);
        StageTransitionSupport transitionSupport = new StageTransitionSupport(
                runRepository,
                artifactStore,
                eventLogStore,
                new DiagnosisAgent(noopProvider(), artifactStore, new ObjectMapper()),
                new RepairAgent(),
                new StageFlowPolicy(),
                new devflow.agent.orchestrator.WorkflowArtifactRenderer()
        );
        StageOperationExecutor stageOperationExecutor = new StageOperationExecutor(
                null,
                null,
                eventLogStore,
                new GenerationEngine(),
                new StageOperationPolicy()
        ) {
            @Override
            public String composeStageArtifact(
                    Path projectPath,
                    RunRecord runRecord,
                    StageType stageType,
                    StageExecution stageExecution,
                    String note
            ) {
                return "# Canonical Artifact\n\n主体内容";
            }
        };
        StageEntryExecutor executor = new StageEntryExecutor(
                runRepository,
                artifactStore,
                eventLogStore,
                stageOperationExecutor,
                transitionSupport
        );
        RunRecord runRecord = runRepository.save(newRunRecord());

        RunRecord entered = executor.enterStage(
                runRecord,
                StageType.PRD,
                RunStatus.IN_PROGRESS,
                """
                <!-- DEVFLOW:EXECUTION_DIRECTIVES:BEGIN -->
                {"fixMode":"PATCH"}
                <!-- DEVFLOW:EXECUTION_DIRECTIVES:END -->

                ## Revision Summary
                只修当前阶段
                """
        );

        Path runDir = tempDir.resolve(".devflow/runs").resolve(entered.runId().toString());
        assertTrue(Files.exists(runDir.resolve(AuxiliaryArtifactNames.stageDirective(StageType.PRD))));
        assertTrue(Files.exists(runDir.resolve("prd_directive.attempt-1.md")));
        String canonicalArtifact = Files.readString(runDir.resolve("prd.md"));
        String directiveSidecar = Files.readString(runDir.resolve(AuxiliaryArtifactNames.stageDirective(StageType.PRD)));
        assertEquals("# Canonical Artifact\n\n主体内容", canonicalArtifact);
        assertTrue(directiveSidecar.contains("DEVFLOW:EXECUTION_DIRECTIVES:BEGIN"));
        assertTrue(directiveSidecar.contains("只修当前阶段"));
        assertTrue(canonicalArtifact.indexOf("Revision Summary") < 0);
    }

    private RunRecord newRunRecord() {
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "goal",
                "constraints",
                new RunConfig(Map.of(), 5),
                StageType.ANALYSIS,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }

    private LlmProvider noopProvider() {
        return new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "{}";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
    }
}
