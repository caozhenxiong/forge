package devflow.agent.artifact;

import devflow.agent.orchestrator.FileRunRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EventLogStore {

    private final FileRunRepository runRepository;

    public EventLogStore(FileRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    public void append(Path projectPath, UUID runId, String message) {
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path logPath = runDir.resolve("events.log");
        String line = "%s %s%n".formatted(Instant.now(), message);
        try {
            Files.createDirectories(runDir);
            Files.writeString(
                    logPath,
                    line,
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to append event log for run " + runId, exception);
        }
    }

    public String read(Path projectPath, UUID runId) {
        Path logPath = runRepository.runDirectory(projectPath, runId).resolve("events.log");
        try {
            if (!Files.exists(logPath)) {
                return "";
            }
            return Files.readString(logPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read event log for run " + runId, exception);
        }
    }
}
