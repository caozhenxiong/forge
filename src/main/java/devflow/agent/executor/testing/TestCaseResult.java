package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

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
