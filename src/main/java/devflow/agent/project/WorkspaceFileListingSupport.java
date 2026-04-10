package devflow.agent.project;

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.util.ProjectPathSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 工作区文件枚举与上下文采样支撑。
 */
final class WorkspaceFileListingSupport {

    private static final long MAX_LISTABLE_FILE_BYTES = 256L * 1024L;

    WorkspaceFileListingSupport() {
    }

    List<Path> listProjectFiles(Path projectPath) {
        try (Stream<Path> stream = Files.walk(projectPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(projectPath, path))
                    .map(projectPath::relativize)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list project files: " + projectPath, exception);
        }
    }

    String collectContext(
            Path projectPath,
            int maxFiles,
            int maxCharsPerFile,
            int maxTotalChars,
            FileProjectWorkspace workspace
    ) {
        List<Path> files = listProjectFiles(projectPath);
        StringBuilder builder = new StringBuilder();
        int usedChars = 0;
        int usedFiles = 0;

        for (Path relativePath : files) {
            if (usedFiles >= maxFiles || usedChars >= maxTotalChars) {
                break;
            }
            String content = workspace.readFile(projectPath, relativePath);
            if (content.isBlank()) {
                continue;
            }
            String truncated = PlaceholderValues.truncateTail(content, maxCharsPerFile);
            String rendered = """
                    ## File: %s

                    ```text
                    %s
                    ```

                    """.formatted(relativePath, truncated);
            if (usedChars + rendered.length() > maxTotalChars) {
                break;
            }
            builder.append(rendered);
            usedChars += rendered.length();
            usedFiles++;
        }

        return builder.toString();
    }

    private boolean isIgnored(Path projectPath, Path path) {
        Path relativePath = projectPath.relativize(path);
        for (Path part : relativePath) {
            String value = part.toString();
            if (ProjectPathSupport.isIgnoredWorkspaceDirectoryName(value)) {
                return true;
            }
        }

        String fileName = relativePath.getFileName().toString();
        if (ProjectPathSupport.isIgnoredWorkspaceArtifact(fileName)) {
            return true;
        }

        try {
            return Files.size(path) > MAX_LISTABLE_FILE_BYTES;
        } catch (IOException exception) {
            return true;
        }
    }
}
