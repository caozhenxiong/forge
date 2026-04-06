package devflow.agent.executor;

import java.util.List;

public record TestCaseSpec(
        String id,
        String title,
        String type,
        boolean required,
        String entry,
        String preconditions,
        String expected,
        List<TestStepSpec> steps
) {
}
