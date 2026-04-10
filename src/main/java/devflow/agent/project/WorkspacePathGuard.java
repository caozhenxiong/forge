package devflow.agent.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 工作区路径边界与空目录清理支撑。
 */
final class WorkspacePathGuard {

    Path resolve(Path projectPath, Path relativePath) {
        Path normalizedProject = projectPath.normalize();
        Path resolved = normalizedProject.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedProject)) {
            throw new IllegalArgumentException("Path escapes project root: " + relativePath);
        }
        return resolved;
    }

    void deleteEmptyParents(Path projectRoot, Path current) throws IOException {
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
}
