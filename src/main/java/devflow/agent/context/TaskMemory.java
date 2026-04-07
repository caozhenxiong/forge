package devflow.agent.context;

import java.util.List;

public record TaskMemory(
        String goal,
        String constraints,
        String currentStageSummary,
        String upstreamContractSummary,
        String recentHistorySummary,
        String failureSummary,
        String repairSummary,
        String workingSetSummary,
        List<FailureDigest> recentFailures
) {

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder("""
                # Task Memory

                ## Goal

                %s

                ## Constraints

                %s

                ## Current Stage Summary

                %s

                ## Upstream Contract Summary

                %s

                ## Recent History Summary

                %s

                ## Failure Summary

                %s

                ## Repair Summary

                %s

                ## Working Set Summary

                %s

                ## Recent Failures

                """.formatted(
                blank(goal),
                blank(constraints),
                blank(currentStageSummary),
                blank(upstreamContractSummary),
                blank(recentHistorySummary),
                blank(failureSummary),
                blank(repairSummary),
                blank(workingSetSummary)
        ));
        if (recentFailures == null || recentFailures.isEmpty()) {
            builder.append("- (none)\n");
        } else {
            for (FailureDigest failure : recentFailures) {
                builder.append(failure.toMarkdown()).append("\n\n");
            }
        }
        return builder.toString().trim();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
