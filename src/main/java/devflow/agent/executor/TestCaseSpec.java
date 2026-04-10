package devflow.agent.executor;

import devflow.agent.quality.CapabilitySurface;
import java.util.List;

public record TestCaseSpec(
        String id,
        String title,
        String type,
        boolean required,
        String entry,
        String preconditions,
        String expected,
        List<TestStepSpec> steps,
        List<CapabilitySurface> capabilities
) {

    public TestCaseSpec {
        steps = steps == null ? List.of() : List.copyOf(steps);
        capabilities = capabilities == null ? List.of() : capabilities.stream()
                .filter(capability -> capability != null)
                .distinct()
                .toList();
    }

    public TestCaseSpec(
            String id,
            String title,
            String type,
            boolean required,
            String entry,
            String preconditions,
            String expected,
            List<TestStepSpec> steps
    ) {
        this(id, title, type, required, entry, preconditions, expected, steps, List.of());
    }
}
