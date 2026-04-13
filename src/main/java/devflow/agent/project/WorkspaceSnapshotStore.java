package devflow.agent.project;

import devflow.agent.executor.generation.GenerationBudgetProfile;

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.util.DevflowPathSupport;
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

    /**
     * reviewer 需要看到“完整 changed-file manifest + 自适应 excerpts”。
     *
     * <p>这里不再按固定前 N 个文件截断。所有 changed paths 都必须显式列出，
     * excerpt 只在现有字符预算内自适应裁剪，并明确标记截断状态。
     */
    public ReviewChangePack buildReviewChangePack(Path projectPath, UUID runId) {
        List<WorkspaceChange> changes = detectChanges(projectPath, runId);
        if (changes.isEmpty()) {
            return new ReviewChangePack(
                    "# Code Change Pack\n\n- changedFiles: 0\n- truncated: false\n\n## Changed File Manifest\n\n- (none)\n",
                    "",
                    false
            );
        }
        String manifest = renderChangeManifest(changes);
        RenderedExcerpt excerpt = renderChangeExcerpts(changes, GenerationBudgetProfile.fileContextPreviewChars());
        return new ReviewChangePack(manifest, excerpt.markdown(), excerpt.truncated());
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
        return DevflowPathSupport.baselineRoot(projectPath, runId);
    }

    private String renderChangeManifest(List<WorkspaceChange> changes) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Code Change Pack\n\n")
                .append("- changedFiles: ").append(changes.size()).append('\n');
        for (WorkspaceChange change : changes) {
            if (change == null || change.path() == null) {
                continue;
            }
            builder.append("- ")
                    .append(change.type())
                    .append(": ")
                    .append(change.path().toString().replace('\\', '/'))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private RenderedExcerpt renderChangeExcerpts(List<WorkspaceChange> changes, int totalBudgetChars) {
        StringBuilder builder = new StringBuilder("\n\n## File Excerpts\n\n");
        int remainingBudget = Math.max(0, totalBudgetChars);
        boolean truncated = false;
        for (int index = 0; index < changes.size(); index++) {
            WorkspaceChange change = changes.get(index);
            if (change == null) {
                continue;
            }
            int remainingChanges = Math.max(1, changes.size() - index);
            int excerptBudget = remainingBudget <= 0 ? 0 : remainingBudget / remainingChanges;
            RenderedExcerpt excerpt = renderSingleExcerpt(change, excerptBudget);
            builder.append(excerpt.markdown());
            remainingBudget = Math.max(0, remainingBudget - excerpt.consumedChars());
            truncated = truncated || excerpt.truncated();
        }
        return new RenderedExcerpt(builder.toString().trim(), truncated, Math.max(0, totalBudgetChars - remainingBudget));
    }

    private RenderedExcerpt renderSingleExcerpt(WorkspaceChange change, int excerptBudgetChars) {
        String path = change.path() == null ? PlaceholderValues.machineNone() : change.path().toString().replace('\\', '/');
        StringBuilder builder = new StringBuilder("### " + change.type() + ": " + path + "\n\n");
        int consumedChars = 0;
        boolean truncated = false;

        List<ExcerptSection> sections = new ArrayList<>();
        if (change.baselineContent() != null && !change.baselineContent().isBlank()) {
            sections.add(new ExcerptSection("Before", change.baselineContent()));
        }
        if (change.currentContent() != null && !change.currentContent().isBlank()) {
            sections.add(new ExcerptSection("After", change.currentContent()));
        }
        if (sections.isEmpty()) {
            builder.append("- ").append(PlaceholderValues.machineEmpty()).append("\n\n");
            return new RenderedExcerpt(builder.toString(), false, consumedChars);
        }

        int remainingBudget = Math.max(0, excerptBudgetChars);
        for (int index = 0; index < sections.size(); index++) {
            ExcerptSection section = sections.get(index);
            int remainingSections = Math.max(1, sections.size() - index);
            int sectionBudget = remainingBudget <= 0 ? 0 : remainingBudget / remainingSections;
            if (sectionBudget <= 0) {
                truncated = true;
                builder.append("#### ").append(section.label()).append("\n\n")
                        .append("```text\n")
                        .append(PlaceholderValues.TRUNCATED_SUFFIX.trim())
                        .append("\n```\n\n");
                continue;
            }
            String rendered = trim(section.content(), sectionBudget);
            int consumed = Math.min(section.content().length(), sectionBudget);
            truncated = truncated || rendered.endsWith(PlaceholderValues.TRUNCATED_SUFFIX);
            consumedChars += consumed;
            remainingBudget = Math.max(0, remainingBudget - consumed);
            builder.append("#### ").append(section.label()).append("\n\n")
                    .append("```text\n")
                    .append(rendered)
                    .append("\n```\n\n");
        }
        return new RenderedExcerpt(builder.toString(), truncated, consumedChars);
    }

    private String trim(String content, int maxCharsPerFile) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return PlaceholderValues.truncateTail(content, maxCharsPerFile);
    }

    public record ReviewChangePack(
            String manifestMarkdown,
            String excerptsMarkdown,
            boolean truncated
    ) {
        public ReviewChangePack {
            manifestMarkdown = manifestMarkdown == null ? "" : manifestMarkdown.trim();
            excerptsMarkdown = excerptsMarkdown == null ? "" : excerptsMarkdown.trim();
        }

        public String toMarkdown() {
            StringBuilder builder = new StringBuilder(manifestMarkdown);
            builder.append('\n')
                    .append("- truncated: ")
                    .append(truncated);
            if (!excerptsMarkdown.isBlank()) {
                builder.append("\n\n").append(excerptsMarkdown);
            }
            return builder.toString().trim();
        }
    }

    private record ExcerptSection(
            String label,
            String content
    ) {
    }

    private record RenderedExcerpt(
            String markdown,
            boolean truncated,
            int consumedChars
    ) {
    }
}
