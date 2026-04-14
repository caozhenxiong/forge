package devflow.agent.context;

import devflow.agent.domain.StageType;
import java.util.stream.Collectors;

public final class ContextProjectionSummaryAssembler {

    private static final int CURRENT_STAGE_SUMMARY_CHAR_BUDGET = 1800;
    private static final int RECENT_HISTORY_CHAR_BUDGET = 2200;
    private static final int REPAIR_SUMMARY_CHAR_BUDGET = 1800;
    private static final int WORKING_SET_CHAR_BUDGET = 2200;

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
                CURRENT_STAGE_SUMMARY_CHAR_BUDGET
        );
        String recentHistorySummary = summaryBuilder.summarizeMarkdown(
                artifacts.recentHistory(),
                RECENT_HISTORY_CHAR_BUDGET
        );
        String repairSummary = summaryBuilder.summarizeMarkdown(
                artifacts.repairBrief(),
                REPAIR_SUMMARY_CHAR_BUDGET
        );
        String workingSetSummary = summaryBuilder.summarizeMarkdown(
                artifacts.workingSet(),
                WORKING_SET_CHAR_BUDGET
        );
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
