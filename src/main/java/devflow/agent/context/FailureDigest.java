package devflow.agent.context;

import devflow.agent.orchestrator.StageType;

public record FailureDigest(
        StageType stageType,
        String summary,
        String changeRequest,
        String evidence,
        String actionItems
) {

    public String toMarkdown() {
        return """
                - stage: %s
                - summary: %s
                - changeRequest: %s
                - evidence: %s
                - actionItems: %s
                """.formatted(
                stageType,
                blank(summary),
                blank(changeRequest),
                blank(evidence),
                blank(actionItems)
        ).trim();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
