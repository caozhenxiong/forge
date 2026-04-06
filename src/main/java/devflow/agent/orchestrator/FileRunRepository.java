package devflow.agent.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class FileRunRepository implements RunRepository {

    private final ObjectMapper objectMapper;

    public FileRunRepository() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void initialize(Path projectPath) {
        try {
            Files.createDirectories(runsRoot(projectPath));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize devflow workspace: " + projectPath, exception);
        }
    }

    @Override
    public RunRecord save(RunRecord runRecord) {
        Path runDir = runDirectory(runRecord.projectPath(), runRecord.runId());
        try {
            Files.createDirectories(runDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(runDir.resolve("run.json").toFile(), runRecord);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save run " + runRecord.runId(), exception);
        }
        return runRecord;
    }

    @Override
    public Optional<RunRecord> findById(Path projectPath, UUID runId) {
        Path runFile = runDirectory(projectPath, runId).resolve("run.json");
        if (!Files.exists(runFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(runFile.toFile(), RunRecord.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load run " + runId, exception);
        }
    }

    public Path runDirectory(Path projectPath, UUID runId) {
        return runsRoot(projectPath).resolve(runId.toString());
    }

    private Path runsRoot(Path projectPath) {
        return projectPath.resolve(".devflow").resolve("runs");
    }
}

