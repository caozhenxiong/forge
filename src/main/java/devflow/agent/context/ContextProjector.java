package devflow.agent.context;

import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContextProjector {

    private final FileArtifactStore artifactStore;
    private final FileProjectWorkspace workspace;
    private final ArtifactSummaryBuilder summaryBuilder;

    public ContextProjector(
            FileArtifactStore artifactStore,
            FileProjectWorkspace workspace,
            ArtifactSummaryBuilder summaryBuilder
    ) {
        this.artifactStore = artifactStore;
        this.workspace = workspace;
        this.summaryBuilder = summaryBuilder;
    }

    public ProjectedContext project(Path projectPath, RunRecord runRecord, StageType currentStage) {
        String currentStageSummary = summaryBuilder.summarizeMarkdown(readCurrentArtifact(projectPath, runRecord, currentStage), 1800);
        String upstreamContractSummary = summaryBuilder.summarizeMarkdown(readUpstreamContract(projectPath, runRecord, currentStage), 2600);
        String recentHistorySummary = summaryBuilder.summarizeMarkdown(readRecentHistory(projectPath, runRecord, currentStage), 2200);
        String repairSummary = summaryBuilder.summarizeMarkdown(readRepairBrief(projectPath, runRecord), 1800);
        String workingSetSummary = summaryBuilder.summarizeMarkdown(workspace.collectContext(projectPath, 6, 900, 5000), 2200);
        List<FailureDigest> failures = collectRecentFailures(projectPath, runRecord);
        String failureSummary = failures.isEmpty()
                ? ""
                : summaryBuilder.renderBulletList(
                failures.stream()
                        .map(failure -> failure.stageType() + ": " + blank(failure.summary()) + " | " + blank(failure.changeRequest()))
                        .toList()
        );

        TaskMemory taskMemory = new TaskMemory(
                runRecord.goal(),
                runRecord.constraints(),
                currentStageSummary,
                upstreamContractSummary,
                recentHistorySummary,
                failureSummary,
                repairSummary,
                workingSetSummary,
                failures
        );
        return new ProjectedContext(
                currentStageSummary,
                upstreamContractSummary,
                recentHistorySummary,
                failureSummary,
                repairSummary,
                workingSetSummary,
                taskMemory
        );
    }

    private String readCurrentArtifact(Path projectPath, RunRecord runRecord, StageType stageType) {
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

    private String readUpstreamContract(Path projectPath, RunRecord runRecord, StageType currentStage) {
        StringBuilder builder = new StringBuilder();
        for (StageType stageType : List.of(StageType.ANALYSIS, StageType.PRD, StageType.DESIGN)) {
            if (stageType.ordinal() > currentStage.ordinal()) {
                break;
            }
            String artifact = readCurrentArtifact(projectPath, runRecord, stageType);
            if (artifact.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("## ").append(stageType).append("\n").append(artifact);
        }
        return builder.toString();
    }

    private String readRecentHistory(Path projectPath, RunRecord runRecord, StageType currentStage) {
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
                builder.append(tail(history, 1600));
            } catch (Exception ignored) {
            }
        }
        return builder.toString();
    }

    private String readRepairBrief(Path projectPath, RunRecord runRecord) {
        Path path = projectPath.resolve(".devflow").resolve("runs").resolve(runRecord.runId().toString()).resolve("repair_brief.md");
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (Exception exception) {
            return "";
        }
    }

    private List<FailureDigest> collectRecentFailures(Path projectPath, RunRecord runRecord) {
        List<FailureDigest> failures = new ArrayList<>();
        for (StageType stageType : StageType.values()) {
            StageExecution execution = runRecord.stageStates().get(stageType);
            if (execution == null || execution.reviewDecision() == null) {
                continue;
            }
            if (execution.reviewDecision().name().equals("APPROVED")) {
                continue;
            }
            String evidence = "";
            String actionItems = "";
            try {
                String reviewArtifact = artifactStore.readReviewArtifact(projectPath, runRecord.runId(), stageType);
                evidence = extractLine(reviewArtifact, "- evidence:");
                actionItems = extractLine(reviewArtifact, "- actionItems:");
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

    private String extractLine(String content, String prefix) {
        if (content == null || content.isBlank()) {
            return "";
        }
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return "";
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
