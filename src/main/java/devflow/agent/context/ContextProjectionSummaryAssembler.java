package devflow.agent.context;

import devflow.agent.domain.StageType;
import java.util.stream.Collectors;

public final class ContextProjectionSummaryAssembler {

    private final ArtifactSummaryBuilder summaryBuilder;

    public ContextProjectionSummaryAssembler(ArtifactSummaryBuilder summaryBuilder) {
        this.summaryBuilder = summaryBuilder;
    }

    ContextProjectionSummaries summarize(
            StageType currentStage,
            ContextProjectionArtifacts artifacts,
            ContextProjectionContractBundle contracts
    ) {
        String currentStageSummary = summaryBuilder.summarizeMarkdown(
                ArtifactContextSanitizer.sanitizeForProjection(
                        artifacts.currentArtifact(),
                        currentStage,
                        contracts.authorityCorpus()
                ),
                1800
        );
        String recentHistorySummary = summaryBuilder.summarizeMarkdown(artifacts.recentHistory(), 2200);
        String repairSummary = summaryBuilder.summarizeMarkdown(artifacts.repairBrief(), 1800);
        String workingSetSummary = summaryBuilder.summarizeMarkdown(artifacts.workingSet(), 2200);
        String failureSummary = artifacts.failures().isEmpty()
                ? ""
                : summaryBuilder.renderBulletList(
                        artifacts.failures().stream()
                                .map(failure -> failure.stageType() + ": " + blank(failure.summary()) + " | " + blank(failure.changeRequest()))
                                .collect(Collectors.toList())
                );
        return new ContextProjectionSummaries(
                currentStageSummary,
                recentHistorySummary,
                repairSummary,
                workingSetSummary,
                failureSummary
        );
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
