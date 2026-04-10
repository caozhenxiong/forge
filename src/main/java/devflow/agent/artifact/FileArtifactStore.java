package devflow.agent.artifact;

import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.orchestrator.StageType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FileArtifactStore implements ArtifactStore {

    private final FileRunRepository runRepository;
    private final ArtifactFileIoSupport artifactFileIoSupport;

    public FileArtifactStore(FileRunRepository runRepository) {
        this.runRepository = runRepository;
        this.artifactFileIoSupport = new ArtifactFileIoSupport();
    }

    @Override
    public Path writeArtifact(UUID runId, StageType stageType, String content) {
        throw new UnsupportedOperationException("Use writeArtifact(projectPath, runId, stageType, content)");
    }

    public Path writeArtifact(Path projectPath, UUID runId, StageType stageType, String content) {
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path artifactPath = runDir.resolve(fileName(stageType));
        return artifactFileIoSupport.writeString(runDir, artifactPath, runId, "artifact", content);
    }

    @Override
    public String readArtifact(UUID runId, StageType stageType) {
        throw new UnsupportedOperationException("Use readArtifact(projectPath, runId, stageType)");
    }

    public String readArtifact(Path projectPath, UUID runId, StageType stageType) {
        Path artifactPath = runRepository.runDirectory(projectPath, runId).resolve(fileName(stageType));
        return artifactFileIoSupport.readString(artifactPath, runId, "artifact stage " + stageType);
    }

    public Path artifactPath(Path projectPath, UUID runId, StageType stageType) {
        return runRepository.runDirectory(projectPath, runId).resolve(fileName(stageType));
    }

    public Path writeReviewArtifact(Path projectPath, UUID runId, StageType stageType, String content) {
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path artifactPath = runDir.resolve(reviewFileName(stageType));
        return artifactFileIoSupport.writeString(runDir, artifactPath, runId, "review artifact", content);
    }

    public String readReviewArtifact(Path projectPath, UUID runId, StageType stageType) {
        Path artifactPath = runRepository.runDirectory(projectPath, runId).resolve(reviewFileName(stageType));
        return artifactFileIoSupport.readString(artifactPath, runId, "review artifact stage " + stageType);
    }

    public Path appendReviewHistory(Path projectPath, UUID runId, StageType stageType, String content) {
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path artifactPath = runDir.resolve(reviewHistoryFileName(stageType));
        return artifactFileIoSupport.appendString(runDir, artifactPath, runId, "review history", content);
    }

    public String readReviewHistory(Path projectPath, UUID runId, StageType stageType) {
        Path artifactPath = runRepository.runDirectory(projectPath, runId).resolve(reviewHistoryFileName(stageType));
        return artifactFileIoSupport.readOptionalString(artifactPath, runId, "review history stage " + stageType);
    }

    public Path writeAuxiliaryArtifact(Path projectPath, UUID runId, String fileName, String content) {
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path artifactPath = runDir.resolve(fileName);
        return artifactFileIoSupport.writeString(runDir, artifactPath, runId, "auxiliary artifact: " + fileName, content);
    }

    public Path appendAuxiliaryArtifact(Path projectPath, UUID runId, String fileName, String content) {
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path artifactPath = runDir.resolve(fileName);
        return artifactFileIoSupport.appendString(runDir, artifactPath, runId, "auxiliary artifact: " + fileName, content);
    }

    /**
     * implementation 这类长阶段需要保留每轮 attempt 的状态快照，避免新一轮修复把上一轮
     * 已经验证过的计划和执行证据覆盖掉。这里在写当前文件的同时，额外落一份 attempt 归档。
     */
    public Path writeAttemptScopedAuxiliaryArtifact(
            Path projectPath,
            UUID runId,
            String fileName,
            int attempt,
            String content
    ) {
        Path latest = writeAuxiliaryArtifact(projectPath, runId, fileName, content);
        if (attempt <= 0) {
            return latest;
        }
        String scopedFileName = attemptScopedFileName(fileName, attempt);
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path artifactPath = runDir.resolve(scopedFileName);
        return artifactFileIoSupport.writeString(
                runDir,
                artifactPath,
                runId,
                "attempt-scoped auxiliary artifact: " + scopedFileName,
                content
        );
    }

    public String readAuxiliaryArtifact(Path projectPath, UUID runId, String fileName) {
        Path artifactPath = runRepository.runDirectory(projectPath, runId).resolve(fileName);
        return artifactFileIoSupport.readOptionalString(artifactPath, runId, "auxiliary artifact: " + fileName);
    }

    /**
     * 重试修复时优先读取上一轮 attempt 的快照，而不是当前滚动文件。
     * 这样即使当前 attempt 已开始增量落盘，也不会覆盖上一轮的稳定计划结构。
     */
    public String readLatestAttemptScopedAuxiliaryArtifact(
            Path projectPath,
            UUID runId,
            String fileName,
            int maxAttemptInclusive
    ) {
        if (maxAttemptInclusive <= 0) {
            return "";
        }
        Path runDir = runRepository.runDirectory(projectPath, runId);
        for (int attempt = maxAttemptInclusive; attempt >= 1; attempt--) {
            Path artifactPath = runDir.resolve(attemptScopedFileName(fileName, attempt));
            if (Files.exists(artifactPath)) {
                return artifactFileIoSupport.readString(
                        artifactPath,
                        runId,
                        "attempt-scoped auxiliary artifact: " + fileName
                );
            }
        }
        return "";
    }

    private String fileName(StageType stageType) {
        return StageArtifactNames.artifact(stageType);
    }

    private String reviewFileName(StageType stageType) {
        return StageArtifactNames.review(stageType);
    }

    private String reviewHistoryFileName(StageType stageType) {
        return StageArtifactNames.reviewHistory(stageType);
    }

    private String attemptScopedFileName(String fileName, int attempt) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return fileName + ".attempt-" + attempt;
        }
        return fileName.substring(0, dotIndex) + ".attempt-" + attempt + fileName.substring(dotIndex);
    }
}
