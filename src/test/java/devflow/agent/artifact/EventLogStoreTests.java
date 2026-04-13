package devflow.agent.artifact;

import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.domain.RunRecord;
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
        RunRecord runRecord = repository.save(new devflow.agent.domain.RunRecord(
                java.util.UUID.randomUUID(),
                tempDir,
                "goal",
                "",
                devflow.agent.domain.RunConfig.defaultConfig(),
                devflow.agent.domain.StageType.ANALYSIS,
                devflow.agent.domain.RunStatus.CREATED,
                new java.util.EnumMap<>(java.util.Map.of(
                        devflow.agent.domain.StageType.ANALYSIS,
                        new devflow.agent.domain.StageExecution(
                                devflow.agent.domain.StageType.ANALYSIS,
                                devflow.agent.domain.StageStatus.PENDING,
                                0,
                                null,
                                null,
                                null,
                                null
                        ),
                        devflow.agent.domain.StageType.PRD,
                        new devflow.agent.domain.StageExecution(devflow.agent.domain.StageType.PRD, devflow.agent.domain.StageStatus.PENDING, 0, null, null, null, null),
                        devflow.agent.domain.StageType.DESIGN,
                        new devflow.agent.domain.StageExecution(devflow.agent.domain.StageType.DESIGN, devflow.agent.domain.StageStatus.PENDING, 0, null, null, null, null),
                        devflow.agent.domain.StageType.IMPLEMENTATION,
                        new devflow.agent.domain.StageExecution(devflow.agent.domain.StageType.IMPLEMENTATION, devflow.agent.domain.StageStatus.PENDING, 0, null, null, null, null),
                        devflow.agent.domain.StageType.CODE_REVIEW,
                        new devflow.agent.domain.StageExecution(devflow.agent.domain.StageType.CODE_REVIEW, devflow.agent.domain.StageStatus.PENDING, 0, null, null, null, null),
                        devflow.agent.domain.StageType.TEST,
                        new devflow.agent.domain.StageExecution(devflow.agent.domain.StageType.TEST, devflow.agent.domain.StageStatus.PENDING, 0, null, null, null, null)
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
