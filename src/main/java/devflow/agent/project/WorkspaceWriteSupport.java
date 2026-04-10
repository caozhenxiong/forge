package devflow.agent.project;

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.util.DevflowPathSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 工作区写入与事务提交支撑。
 */
final class WorkspaceWriteSupport {

    private final WorkspacePathGuard pathGuard;

    WorkspaceWriteSupport(WorkspacePathGuard pathGuard) {
        this.pathGuard = pathGuard;
    }

    void writeFile(Path projectPath, Path relativePath, String content) {
        Path path = pathGuard.resolve(projectPath, relativePath);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write file: " + path, exception);
        }
    }

    WriteTransaction stageWrite(Path projectPath, Path relativePath, String content) {
        Path normalizedProject = projectPath.toAbsolutePath().normalize();
        Path targetPath = pathGuard.resolve(normalizedProject, relativePath);
        String transactionId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        Path stagingRoot = DevflowPathSupport.writeTransactionStagingRoot(normalizedProject, transactionId);
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

    String readStagedContent(WriteTransaction transaction) {
        try {
            return Files.readString(transaction.candidatePath());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read staged candidate: " + transaction.candidatePath(), exception);
        }
    }

    void commitWrite(WriteTransaction transaction) {
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

    void failWrite(WriteTransaction transaction, String reason) {
        Path failedRoot = DevflowPathSupport.writeTransactionsFailedRoot(transaction.projectPath())
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
                    reason == null ? PlaceholderValues.UNKNOWN_FAILURE : reason,
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to record failed write transaction for " + transaction.targetPath(), exception);
        }
    }

    void deleteFile(Path projectPath, Path relativePath) {
        Path path = pathGuard.resolve(projectPath, relativePath);
        try {
            Files.deleteIfExists(path);
            pathGuard.deleteEmptyParents(projectPath.normalize(), path.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete file: " + path, exception);
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
}
