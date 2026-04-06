package devflow.agent.executor;

import java.util.List;

public record TestCasePlan(
        String summary,
        List<TestCaseSpec> cases
) {
}
