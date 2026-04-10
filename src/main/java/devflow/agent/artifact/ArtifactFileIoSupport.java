package devflow.agent.artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * 统一处理 run artifact 的底层文件 IO。
 *
 * <p>FileArtifactStore 只保留“写什么文件”的协议语义，
 * 这层负责目录创建、读写和统一异常包装，避免同类 try/catch 在多个入口重复出现。
 */
final class ArtifactFileIoSupport {

    Path writeString(Path runDir, Path artifactPath, UUID runId, String description, String content) {
        try {
            Files.createDirectories(runDir);
            Files.writeString(artifactPath, content);
            return artifactPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write " + description + " for run " + runId, exception);
        }
    }

    Path appendString(Path runDir, Path artifactPath, UUID runId, String description, String content) {
        try {
            Files.createDirectories(runDir);
            Files.writeString(
                    artifactPath,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            return artifactPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to append " + description + " for run " + runId, exception);
        }
    }

    String readString(Path artifactPath, UUID runId, String description) {
        try {
            return Files.readString(artifactPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + description + " for run " + runId, exception);
        }
    }

    String readOptionalString(Path artifactPath, UUID runId, String description) {
        try {
            if (!Files.exists(artifactPath)) {
                return "";
            }
            return Files.readString(artifactPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + description + " for run " + runId, exception);
        }
    }
}
