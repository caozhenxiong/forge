package devflow.agent.artifact;

import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.orchestrator.RunRecord;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogStoreTests {

    @TempDir
    Path tempDir;

    @Test
    void appendCollapsesMultiLineMessagesIntoSingleEventLine() {
        FileRunRepository repository = new FileRunRepository();
        repository.initialize(tempDir);
        RunRecord runRecord = repository.save(new devflow.agent.orchestrator.RunRecord(
                java.util.UUID.randomUUID(),
                tempDir,
                "goal",
                "",
                devflow.agent.orchestrator.RunConfig.defaultConfig(),
                devflow.agent.orchestrator.StageType.ANALYSIS,
                devflow.agent.orchestrator.RunStatus.CREATED,
                new java.util.EnumMap<>(java.util.Map.of(
                        devflow.agent.orchestrator.StageType.ANALYSIS,
                        new devflow.agent.orchestrator.StageExecution(
                                devflow.agent.orchestrator.StageType.ANALYSIS,
                                devflow.agent.orchestrator.StageStatus.PENDING,
                                0,
                                null,
                                null,
                                null,
                                null
                        ),
                        devflow.agent.orchestrator.StageType.PRD,
                        new devflow.agent.orchestrator.StageExecution(devflow.agent.orchestrator.StageType.PRD, devflow.agent.orchestrator.StageStatus.PENDING, 0, null, null, null, null),
                        devflow.agent.orchestrator.StageType.DESIGN,
                        new devflow.agent.orchestrator.StageExecution(devflow.agent.orchestrator.StageType.DESIGN, devflow.agent.orchestrator.StageStatus.PENDING, 0, null, null, null, null),
                        devflow.agent.orchestrator.StageType.IMPLEMENTATION,
                        new devflow.agent.orchestrator.StageExecution(devflow.agent.orchestrator.StageType.IMPLEMENTATION, devflow.agent.orchestrator.StageStatus.PENDING, 0, null, null, null, null),
                        devflow.agent.orchestrator.StageType.CODE_REVIEW,
                        new devflow.agent.orchestrator.StageExecution(devflow.agent.orchestrator.StageType.CODE_REVIEW, devflow.agent.orchestrator.StageStatus.PENDING, 0, null, null, null, null),
                        devflow.agent.orchestrator.StageType.TEST,
                        new devflow.agent.orchestrator.StageExecution(devflow.agent.orchestrator.StageType.TEST, devflow.agent.orchestrator.StageStatus.PENDING, 0, null, null, null, null)
                )),
                java.time.Instant.now(),
                java.time.Instant.now()
        ));
        EventLogStore store = new EventLogStore(repository);

        store.append(tempDir, runRecord.runId(), "GENERATION failed evidence=line1\nline2\nline3");

        String log = store.read(tempDir, runRecord.runId());
        assertTrue(log.contains("GENERATION failed evidence=line1 | line2 | line3"));
        assertFalse(log.contains("line1\nline2"));
    }
}
