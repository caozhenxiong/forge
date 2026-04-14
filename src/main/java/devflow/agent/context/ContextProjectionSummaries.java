package devflow.agent.context;

record ContextProjectionSummaries(
        String currentStageSummary,
        String recentHistorySummary,
        String repairSummary,
        String workingSetSummary,
        String failureSummary
) {
}
