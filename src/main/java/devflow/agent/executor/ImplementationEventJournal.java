package devflow.agent.executor;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.orchestrator.RunRecord;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * implementation 阶段的事件日志收集器。
 *
 * <p>所有 implementation 相关事件都先进入这本 journal，再镜像写入 run 级 events.log。
 * 这样 implementation 的 markdown/json/事件产物和 events.log 可以尽量共享同一份运行时真相。
 */
class ImplementationEventJournal {

    private final EventLogStore eventLogStore;
    private final FileArtifactStore fileArtifactStore;
    private final Path projectPath;
    private final RunRecord runRecord;
    private final List<ImplementationEventEntry> entries = new ArrayList<>();

    ImplementationEventJournal(
            EventLogStore eventLogStore,
            FileArtifactStore fileArtifactStore,
            Path projectPath,
            RunRecord runRecord
    ) {
        this.eventLogStore = eventLogStore;
        this.fileArtifactStore = fileArtifactStore;
        this.projectPath = projectPath;
        this.runRecord = runRecord;
    }

    void append(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Instant timestamp = Instant.now();
        String normalized = EventLogStore.normalizeMessageLine(message);
        entries.add(new ImplementationEventEntry(timestamp, normalized));
        if (eventLogStore != null && runRecord != null) {
            eventLogStore.append(projectPath, runRecord.runId(), timestamp, normalized);
        }
    }

    List<ImplementationEventEntry> snapshot() {
        return List.copyOf(entries);
    }

    Path appendSyntaxRepairFailureArtifact(
            Path relativePath,
            String unitLabel,
            String evidence,
            String content
    ) {
        if (fileArtifactStore == null || runRecord == null || content == null || content.isBlank()) {
            return null;
        }
        String normalizedEvidence = evidence == null || evidence.isBlank() ? "未知" : evidence;
        String fencedLanguage = fencedLanguage(relativePath);
        String markdown = """
                ## %s

                - 文件: `%s`
                - 单元: `%s`
                - 证据: %s

                ```%s
                %s
                ```

                """.formatted(
                Instant.now(),
                relativePath,
                unitLabel,
                normalizedEvidence,
                fencedLanguage,
                content
        );
        return fileArtifactStore.appendAuxiliaryArtifact(
                projectPath,
                runRecord.runId(),
                AuxiliaryArtifactNames.SYNTAX_REPAIR_FAILURES,
                markdown
        );
    }

    private String fencedLanguage(Path relativePath) {
        if (relativePath == null || relativePath.getFileName() == null) {
            return "text";
        }
        String fileName = relativePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".js") || fileName.endsWith(".mjs") || fileName.endsWith(".cjs")) {
            return "javascript";
        }
        if (fileName.endsWith(".ts")) {
            return "typescript";
        }
        if (fileName.endsWith(".tsx")) {
            return "tsx";
        }
        if (fileName.endsWith(".css")) {
            return "css";
        }
        if (fileName.endsWith(".html")) {
            return "html";
        }
        return "text";
    }
}
