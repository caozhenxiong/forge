package devflow.agent.project;

import java.nio.file.Path;

public record WriteTransaction(
        String id,
        Path projectPath,
        Path relativePath,
        Path targetPath,
        Path stagingRoot,
        Path candidatePath,
        Path originalSnapshotPath
) {
}
