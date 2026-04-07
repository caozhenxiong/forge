package devflow.agent.executor;

public record TestCaseResult(
        String id,
        String title,
        TestCaseStatus status,
        boolean required,
        String details,
        String failureReason,
        String evidence
) {

    public boolean passed() {
        return status == TestCaseStatus.PASSED;
    }

    public boolean blocked() {
        return status == TestCaseStatus.BLOCKED;
    }
}
