package devflow.agent.artifact;

import devflow.agent.orchestrator.FileRunRepository;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileArtifactStoreTests {

    @TempDir
    Path tempDir;

    @Test
    void attemptScopedAuxiliaryArtifactsAreArchivedAndReadableByPreviousAttempt() {
        FileRunRepository repository = new FileRunRepository();
        repository.initialize(tempDir);
        FileArtifactStore store = new FileArtifactStore(repository);
        UUID runId = UUID.randomUUID();

        store.writeAttemptScopedAuxiliaryArtifact(tempDir, runId, "implementation_state.json", 1, "{\"attempt\":1}");
        store.writeAttemptScopedAuxiliaryArtifact(tempDir, runId, "implementation_state.json", 2, "{\"attempt\":2}");

        assertEquals(
                "{\"attempt\":2}",
                store.readAuxiliaryArtifact(tempDir, runId, "implementation_state.json")
        );
        assertEquals(
                "{\"attempt\":1}",
                store.readLatestAttemptScopedAuxiliaryArtifact(tempDir, runId, "implementation_state.json", 1)
        );
        assertEquals(
                "{\"attempt\":2}",
                store.readLatestAttemptScopedAuxiliaryArtifact(tempDir, runId, "implementation_state.json", 2)
        );
    }
}
