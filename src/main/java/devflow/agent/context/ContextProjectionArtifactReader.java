package devflow.agent.context;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ReviewArtifactPayload;
import devflow.agent.protocol.ReviewHistoryEntryPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.util.DevflowPathSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 上下文投影 artifact 读取器。
 *
 * <p>负责投影阶段需要的 artifact/history/repair brief 读取与裁剪，
 * 让 `ContextProjector` 更专注于四层上下文装配。
 */
final class ContextProjectionArtifactReader {

    private final FileArtifactStore artifactStore;

    ContextProjectionArtifactReader(FileArtifactStore artifactStore) {
        this.artifactStore = artifactStore;
    }

    String readCurrentArtifact(Path projectPath, RunRecord runRecord, StageType stageType) {
        try {
            StageExecution execution = runRecord.stageStates().get(stageType);
            if (execution == null || execution.artifactPath() == null) {
                return "";
            }
            return artifactStore.readArtifact(projectPath, runRecord.runId(), stageType);
        } catch (Exception exception) {
            return "";
        }
    }

    String readRecentHistory(Path projectPath, RunRecord runRecord, StageType currentStage) {
        StringBuilder builder = new StringBuilder();
        for (StageType stageType : StageType.values()) {
            if (stageType.ordinal() > currentStage.ordinal()) {
                continue;
            }
            try {
                String history = artifactStore.readReviewHistory(projectPath, runRecord.runId(), stageType);
                if (history == null || history.isBlank()) {
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append("\n\n");
                }
                builder.append("## ").append(stageType).append(" review history\n");
                builder.append(tail(renderStructuredReviewHistory(stageType, history), 1600));
            } catch (Exception ignored) {
            }
        }
        return builder.toString();
    }

    String readRepairBrief(Path projectPath, RunRecord runRecord) {
        Path path = DevflowPathSupport.auxiliaryArtifact(
                projectPath,
                runRecord.runId(),
                AuxiliaryArtifactNames.REPAIR_BRIEF
        );
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (Exception exception) {
            return "";
        }
    }

    List<FailureDigest> collectRecentFailures(Path projectPath, RunRecord runRecord) {
        List<FailureDigest> failures = new ArrayList<>();
        for (StageType stageType : StageType.values()) {
            StageExecution execution = runRecord.stageStates().get(stageType);
            if (execution == null || execution.reviewDecision() == null) {
                continue;
            }
            if (execution.reviewDecision() == devflow.agent.review.ReviewDecision.APPROVED) {
                continue;
            }
            String evidence = "";
            String actionItems = "";
            try {
                String reviewArtifact = artifactStore.readReviewArtifact(projectPath, runRecord.runId(), stageType);
                ReviewArtifactPayload payload = StructuredArtifactBlocks.readFirstJsonBlock(
                        reviewArtifact,
                        ArtifactBlockKind.REVIEW_RESULT,
                        ReviewArtifactPayload.class
                );
                if (payload != null) {
                    evidence = blank(payload.evidence());
                    actionItems = blank(payload.actionItems());
                }
            } catch (Exception ignored) {
            }
            failures.add(new FailureDigest(
                    stageType,
                    execution.reviewSummary(),
                    execution.changeRequest(),
                    evidence,
                    actionItems
            ));
        }
        return failures.size() <= 4 ? failures : failures.subList(failures.size() - 4, failures.size());
    }

    private String renderStructuredReviewHistory(StageType stageType, String history) {
        if (history == null || history.isBlank()) {
            return "";
        }
        List<ReviewHistoryEntryPayload> entries = StructuredArtifactBlocks.readAllJsonBlocks(
                history,
                ArtifactBlockKind.REVIEW_HISTORY_ENTRY,
                ReviewHistoryEntryPayload.class
        );
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ReviewHistoryEntryPayload entry : entries) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("- attempt=").append(entry.attempt())
                    .append(" reviewer=").append(blank(entry.reviewer()))
                    .append(" decision=").append(blank(entry.decision()))
                    .append(" fixMode=").append(blank(entry.fixMode()));
            if (!blank(entry.summary()).isBlank()) {
                builder.append("\n  summary: ").append(entry.summary().trim());
            }
            if (stageType.ordinal() >= StageType.IMPLEMENTATION.ordinal()) {
                if (!blank(entry.evidence()).isBlank()) {
                    builder.append("\n  evidence: ").append(entry.evidence().trim());
                }
                if (!blank(entry.actionItems()).isBlank()) {
                    builder.append("\n  actionItems: ").append(entry.actionItems().trim());
                }
            }
        }
        return builder.toString();
    }

    private String tail(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return blank(content);
        }
        return content.substring(content.length() - maxChars);
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
