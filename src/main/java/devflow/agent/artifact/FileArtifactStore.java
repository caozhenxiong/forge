package devflow.agent.artifact;

import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.orchestrator.StageType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FileArtifactStore implements ArtifactStore {

    private final FileRunRepository runRepository;

    public FileArtifactStore(FileRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public Path writeArtifact(UUID runId, StageType stageType, String content) {
        throw new UnsupportedOperationException("Use writeArtifact(projectPath, runId, stageType, content)");
    }

    public Path writeArtifact(Path projectPath, UUID runId, StageType stageType, String content) {
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path artifactPath = runDir.resolve(fileName(stageType));
        try {
            Files.createDirectories(runDir);
            Files.writeString(artifactPath, content);
            return artifactPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write artifact for run " + runId, exception);
        }
    }

    @Override
    public String readArtifact(UUID runId, StageType stageType) {
        throw new UnsupportedOperationException("Use readArtifact(projectPath, runId, stageType)");
    }

    public String readArtifact(Path projectPath, UUID runId, StageType stageType) {
        Path artifactPath = runRepository.runDirectory(projectPath, runId).resolve(fileName(stageType));
        try {
            return Files.readString(artifactPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read artifact for run " + runId + " stage " + stageType, exception);
        }
    }

    public Path artifactPath(Path projectPath, UUID runId, StageType stageType) {
        return runRepository.runDirectory(projectPath, runId).resolve(fileName(stageType));
    }

    public Path writeReviewArtifact(Path projectPath, UUID runId, StageType stageType, String content) {
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path artifactPath = runDir.resolve(reviewFileName(stageType));
        try {
            Files.createDirectories(runDir);
            Files.writeString(artifactPath, content);
            return artifactPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write review artifact for run " + runId, exception);
        }
    }

    public String readReviewArtifact(Path projectPath, UUID runId, StageType stageType) {
        Path artifactPath = runRepository.runDirectory(projectPath, runId).resolve(reviewFileName(stageType));
        try {
            return Files.readString(artifactPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read review artifact for run " + runId + " stage " + stageType, exception);
        }
    }

    public Path appendReviewHistory(Path projectPath, UUID runId, StageType stageType, String content) {
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path artifactPath = runDir.resolve(reviewHistoryFileName(stageType));
        try {
            Files.createDirectories(runDir);
            Files.writeString(
                    artifactPath,
                    content,
                    java.nio.charset.StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            return artifactPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to append review history for run " + runId, exception);
        }
    }

    public String readReviewHistory(Path projectPath, UUID runId, StageType stageType) {
        Path artifactPath = runRepository.runDirectory(projectPath, runId).resolve(reviewHistoryFileName(stageType));
        try {
            if (!Files.exists(artifactPath)) {
                return "";
            }
            return Files.readString(artifactPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read review history for run " + runId + " stage " + stageType, exception);
        }
    }

    public Path writeAuxiliaryArtifact(Path projectPath, UUID runId, String fileName, String content) {
        Path runDir = runRepository.runDirectory(projectPath, runId);
        Path artifactPath = runDir.resolve(fileName);
        try {
            Files.createDirectories(runDir);
            Files.writeString(artifactPath, content);
            return artifactPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write auxiliary artifact for run " + runId + ": " + fileName, exception);
        }
    }

    private String fileName(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> "analysis.md";
            case PRD -> "prd.md";
            case DESIGN -> "design.md";
            case IMPLEMENTATION -> "implementation.md";
            case CODE_REVIEW -> "code_review.md";
            case TEST -> "test_report.md";
        };
    }

    private String reviewFileName(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> "analysis_review.md";
            case PRD -> "prd_review.md";
            case DESIGN -> "design_review.md";
            case IMPLEMENTATION -> "implementation_review.md";
            case CODE_REVIEW -> "code_review_review.md";
            case TEST -> "test_review.md";
        };
    }

    private String reviewHistoryFileName(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> "analysis_review_history.md";
            case PRD -> "prd_review_history.md";
            case DESIGN -> "design_review_history.md";
            case IMPLEMENTATION -> "implementation_review_history.md";
            case CODE_REVIEW -> "code_review_review_history.md";
            case TEST -> "test_review_history.md";
        };
    }
}
