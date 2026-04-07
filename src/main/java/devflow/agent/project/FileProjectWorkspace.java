package devflow.agent.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class FileProjectWorkspace implements ProjectWorkspace {

    @Override
    public String readFile(Path projectPath, Path relativePath) {
        Path path = resolve(projectPath, relativePath);
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file: " + path, exception);
        }
    }

    @Override
    public void writeFile(Path projectPath, Path relativePath, String content) {
        Path path = resolve(projectPath, relativePath);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write file: " + path, exception);
        }
    }

    public WriteTransaction stageWrite(Path projectPath, Path relativePath, String content) {
        Path normalizedProject = projectPath.toAbsolutePath().normalize();
        Path targetPath = resolve(normalizedProject, relativePath);
        String transactionId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        Path stagingRoot = normalizedProject.resolve(".devflow")
                .resolve("write-transactions")
                .resolve("active")
                .resolve(transactionId);
        Path candidatePath = stagingRoot.resolve("candidate").resolve(relativePath).normalize();
        Path originalSnapshotPath = stagingRoot.resolve("original").resolve(relativePath).normalize();
        try {
            Files.createDirectories(candidatePath.getParent());
            Files.writeString(candidatePath, content, StandardCharsets.UTF_8);
            if (Files.exists(targetPath)) {
                Files.createDirectories(originalSnapshotPath.getParent());
                Files.copy(targetPath, originalSnapshotPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return new WriteTransaction(
                    transactionId,
                    normalizedProject,
                    relativePath,
                    targetPath,
                    stagingRoot,
                    candidatePath,
                    originalSnapshotPath
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to stage write transaction for " + targetPath, exception);
        }
    }

    public String readStagedContent(WriteTransaction transaction) {
        try {
            return Files.readString(transaction.candidatePath());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read staged candidate: " + transaction.candidatePath(), exception);
        }
    }

    public void commitWrite(WriteTransaction transaction) {
        try {
            if (transaction.targetPath().getParent() != null) {
                Files.createDirectories(transaction.targetPath().getParent());
            }
            try {
                Files.move(
                        transaction.candidatePath(),
                        transaction.targetPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (IOException atomicMoveFailure) {
                Files.move(
                        transaction.candidatePath(),
                        transaction.targetPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            deleteRecursively(transaction.stagingRoot());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to commit write transaction for " + transaction.targetPath(), exception);
        }
    }

    public void failWrite(WriteTransaction transaction, String reason) {
        Path failedRoot = transaction.projectPath().resolve(".devflow")
                .resolve("write-transactions")
                .resolve("failed")
                .resolve(transaction.id());
        try {
            if (failedRoot.getParent() != null) {
                Files.createDirectories(failedRoot.getParent());
            }
            if (Files.exists(transaction.stagingRoot())) {
                Files.move(transaction.stagingRoot(), failedRoot, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.createDirectories(failedRoot);
            }
            Files.writeString(
                    failedRoot.resolve("failure.txt"),
                    reason == null ? "(unknown failure)" : reason,
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to record failed write transaction for " + transaction.targetPath(), exception);
        }
    }

    @Override
    public void deleteFile(Path projectPath, Path relativePath) {
        Path path = resolve(projectPath, relativePath);
        try {
            Files.deleteIfExists(path);
            deleteEmptyParents(projectPath.normalize(), path.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete file: " + path, exception);
        }
    }

    @Override
    public List<Path> listProjectFiles(Path projectPath) {
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

    @Override
    public String collectContext(Path projectPath, int maxFiles, int maxCharsPerFile, int maxTotalChars) {
        List<Path> files = listProjectFiles(projectPath);
        StringBuilder builder = new StringBuilder();
        int usedChars = 0;
        int usedFiles = 0;

        for (Path relativePath : files) {
            if (usedFiles >= maxFiles || usedChars >= maxTotalChars) {
                break;
            }
            String content = readFile(projectPath, relativePath);
            if (content.isBlank()) {
                continue;
            }
            String truncated = content.length() > maxCharsPerFile
                    ? content.substring(0, maxCharsPerFile) + "\n...<truncated>"
                    : content;
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

        if (builder.isEmpty()) {
            return "(workspace context unavailable)";
        }
        return builder.toString();
    }

    @Override
    public CommandResult runCommand(Path projectPath, List<String> command, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(projectPath.toFile())
                    .redirectErrorStream(false)
                    .start();

            boolean completed = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CommandResult(124, "", "Command timed out after " + timeout);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), stdout, stderr);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to run command in " + projectPath + ": " + command, exception);
        }
    }

    private Path resolve(Path projectPath, Path relativePath) {
        Path resolved = projectPath.resolve(relativePath).normalize();
        if (!resolved.startsWith(projectPath.normalize())) {
            throw new IllegalArgumentException("Path escapes project root: " + relativePath);
        }
        return resolved;
    }

    private void deleteEmptyParents(Path projectRoot, Path current) throws IOException {
        Path normalizedRoot = projectRoot.normalize();
        Path cursor = current;
        while (cursor != null && !cursor.equals(normalizedRoot)) {
            try (Stream<Path> children = Files.list(cursor)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(cursor);
            cursor = cursor.getParent();
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException("Failed to clean staged path: " + path, exception);
                        }
                    });
        }
    }

    private boolean isIgnored(Path projectPath, Path path) {
        Path relativePath = projectPath.relativize(path);
        for (Path part : relativePath) {
            String value = part.toString();
            if (value.equals(".git")
                    || value.equals(".devflow")
                    || value.equals("target")
                    || value.equals("build")
                    || value.equals("out")
                    || value.equals("node_modules")
                    || value.equals(".idea")) {
                return true;
            }
        }

        String fileName = relativePath.getFileName().toString();
        if (fileName.endsWith(".class") || fileName.endsWith(".jar")) {
            return true;
        }

        try {
            return Files.size(path) > 256 * 1024;
        } catch (IOException exception) {
            return true;
        }
    }
}
