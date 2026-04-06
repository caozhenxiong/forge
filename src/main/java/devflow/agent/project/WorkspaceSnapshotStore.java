package devflow.agent.project;

import devflow.agent.orchestrator.FileRunRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceSnapshotStore {

    private final FileRunRepository runRepository;
    private final FileProjectWorkspace workspace;

    public WorkspaceSnapshotStore(FileRunRepository runRepository, FileProjectWorkspace workspace) {
        this.runRepository = runRepository;
        this.workspace = workspace;
    }

    public void captureBaseline(Path projectPath, UUID runId) {
        Path baselineRoot = baselineRoot(projectPath, runId);
        try {
            // Each run snapshots the starting workspace once so implementation/code-review stages
            // can compare current files against the original project state.
            if (Files.exists(baselineRoot)) {
                try (var stream = Files.walk(baselineRoot)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException exception) {
                                    throw new IllegalStateException(exception);
                                }
                            });
                }
            }
            Files.createDirectories(baselineRoot);
            for (Path relativePath : workspace.listProjectFiles(projectPath)) {
                Path source = projectPath.resolve(relativePath);
                Path target = baselineRoot.resolve(relativePath);
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to capture workspace baseline", exception);
        }
    }

    public List<WorkspaceChange> detectChanges(Path projectPath, UUID runId) {
        Path baselineRoot = baselineRoot(projectPath, runId);
        Set<Path> paths = new LinkedHashSet<>();
        paths.addAll(workspace.listProjectFiles(projectPath));
        paths.addAll(listSnapshotFiles(baselineRoot));

        List<WorkspaceChange> changes = new ArrayList<>();
        for (Path relativePath : paths) {
            Path currentPath = projectPath.resolve(relativePath);
            Path baselinePath = baselineRoot.resolve(relativePath);
            boolean currentExists = Files.exists(currentPath);
            boolean baselineExists = Files.exists(baselinePath);

            if (currentExists && !baselineExists) {
                changes.add(new WorkspaceChange(
                        WorkspaceChange.ChangeType.ADDED,
                        relativePath,
                        "",
                        workspace.readFile(projectPath, relativePath)
                ));
                continue;
            }

            if (!currentExists && baselineExists) {
                changes.add(new WorkspaceChange(
                        WorkspaceChange.ChangeType.DELETED,
                        relativePath,
                        readSnapshotFile(baselineRoot, relativePath),
                        ""
                ));
                continue;
            }

            if (currentExists) {
                String baselineContent = readSnapshotFile(baselineRoot, relativePath);
                String currentContent = workspace.readFile(projectPath, relativePath);
                if (!baselineContent.equals(currentContent)) {
                    changes.add(new WorkspaceChange(
                            WorkspaceChange.ChangeType.MODIFIED,
                            relativePath,
                            baselineContent,
                            currentContent
                    ));
                }
            }
        }
        return changes;
    }

    public String renderChanges(Path projectPath, UUID runId, int maxFiles, int maxCharsPerFile) {
        List<WorkspaceChange> changes = detectChanges(projectPath, runId);
        if (changes.isEmpty()) {
            return "无代码变更。";
        }

        // Reviewer prompts only need a compact diff-like summary, not the full workspace.
        StringBuilder builder = new StringBuilder();
        int rendered = 0;
        for (WorkspaceChange change : changes) {
            if (rendered >= maxFiles) {
                break;
            }
            String currentContent = trim(change.currentContent(), maxCharsPerFile);
            String baselineContent = trim(change.baselineContent(), maxCharsPerFile);
            builder.append("## ").append(change.type()).append(": ").append(change.path()).append("\n\n");
            if (!baselineContent.isBlank()) {
                builder.append("### Before\n\n```text\n").append(baselineContent).append("\n```\n\n");
            }
            if (!currentContent.isBlank()) {
                builder.append("### After\n\n```text\n").append(currentContent).append("\n```\n\n");
            }
            rendered++;
        }
        return builder.toString();
    }

    private List<Path> listSnapshotFiles(Path baselineRoot) {
        if (!Files.exists(baselineRoot)) {
            return List.of();
        }
        try (var stream = Files.walk(baselineRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(baselineRoot::relativize)
                    .sorted(java.util.Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list baseline snapshot", exception);
        }
    }

    private String readSnapshotFile(Path baselineRoot, Path relativePath) {
        Path path = baselineRoot.resolve(relativePath);
        if (!Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read baseline file: " + path, exception);
        }
    }

    private Path baselineRoot(Path projectPath, UUID runId) {
        return runRepository.runDirectory(projectPath, runId).resolve("baseline");
    }

    private String trim(String content, int maxCharsPerFile) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.length() > maxCharsPerFile
                ? content.substring(0, maxCharsPerFile) + "\n...<truncated>"
                : content;
    }
}
