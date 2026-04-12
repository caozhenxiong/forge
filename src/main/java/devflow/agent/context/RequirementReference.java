package devflow.agent.context;

public record RequirementReference(
        String id,
        String category,
        String text,
        CoverageObligation obligation
) {

    public RequirementReference {
        obligation = obligation == null ? CoverageObligation.FINAL_ACCEPTANCE : obligation;
    }

    public boolean requiresPlanningCoverage() {
        return obligation.isPlanningRequired();
    }
}
