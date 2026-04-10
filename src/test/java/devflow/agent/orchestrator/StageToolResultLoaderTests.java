package devflow.agent.orchestrator;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.executor.ToolFailureCode;
import devflow.agent.executor.ToolName;
import devflow.agent.executor.ToolResult;
import devflow.agent.executor.ToolStatus;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.protocol.ToolResultPayload;
import devflow.agent.protocol.ToolResultsArtifactPayload;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageToolResultLoaderTests {

    @TempDir
    Path tempDir;

    @Test
    void loadsStructuredToolFailuresFromTestExecutionArtifact() {
        FileRunRepository runRepository = new FileRunRepository();
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        UUID runId = UUID.randomUUID();
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        RunRecord runRecord = new RunRecord(
                runId,
                tempDir,
                "goal",
                "",
                RunConfig.defaultConfig(),
                StageType.TEST,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
        String executionArtifact = StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.TOOL_RESULTS,
                new ToolResultsArtifactPayload(List.of(
                        new ToolResultPayload(
                                ToolName.TEST_CASE_EXECUTION.name(),
                                ToolStatus.FAILED.name(),
                                ToolFailureCode.TEST_CASE_EXECUTION_FAILED.name(),
                                "execution failed",
                                "rerun tests"
                        )
                ))
        );
        artifactStore.writeAuxiliaryArtifact(tempDir, runId, AuxiliaryArtifactNames.TEST_EXECUTION, executionArtifact);

        StageToolResultSummary summary = new StageToolResultLoader(artifactStore).load(tempDir, runRecord, StageType.TEST);

        assertTrue(summary.blockingFailure());
        assertEquals(1, summary.failedToolCount());
        assertEquals(List.of("TEST_CASE_EXECUTION"), summary.failedTools());
        assertEquals(List.of("TEST_CASE_EXECUTION_FAILED"), summary.failureCodes());
    }
}
