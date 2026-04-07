package devflow.agent.context;

public record ProjectedContext(
        String currentStageSummary,
        String upstreamContractSummary,
        String recentHistorySummary,
        String failureSummary,
        String repairSummary,
        String workingSetSummary,
        TaskMemory taskMemory
) {

    public String toMarkdown() {
        return """
                # Projected Context

                ## Current Stage

                %s

                ## Upstream Contract

                %s

                ## Recent History

                %s

                ## Failure Summary

                %s

                ## Repair Summary

                %s

                ## Working Set

                %s
                """.formatted(
                blank(currentStageSummary),
                blank(upstreamContractSummary),
                blank(recentHistorySummary),
                blank(failureSummary),
                blank(repairSummary),
                blank(workingSetSummary)
        ).trim();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
