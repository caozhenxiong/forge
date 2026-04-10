package devflow.agent.artifact;

import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.text.TextCanonicalizer;
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
        append(projectPath, runId, Instant.now(), message);
    }

    public void append(Path projectPath, UUID runId, Instant timestamp, String message) {
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path logPath = runDir.resolve("events.log");
        String line = "%s %s%n".formatted(timestamp, normalizeMessageLine(message));
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

    /**
     * events.log 需要保持“每个事件一行”，否则多行异常栈会污染后续时间戳和 tail 观察。
     * 这里统一把换行折叠成单行分隔符，保证日志既可读又便于程序化解析。
     */
    public static String normalizeMessageLine(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return TextCanonicalizer.collapseLineBreaks(message, " | ");
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
