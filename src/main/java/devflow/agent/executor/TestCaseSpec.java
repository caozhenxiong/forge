package devflow.agent.executor;

import devflow.agent.quality.CapabilityIds;
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
        List<String> capabilities,
        String observationTargetId,
        TestObservationTrigger observationTrigger,
        TestObservationComparison observationComparison
) {

    public TestCaseSpec {
        steps = steps == null ? List.of() : List.copyOf(steps);
        capabilities = CapabilityIds.normalizeList(capabilities);
        observationTargetId = CapabilityIds.normalize(observationTargetId);
        observationTrigger = observationTrigger == null ? TestObservationTrigger.NONE : observationTrigger;
        observationComparison = observationComparison == null ? TestObservationComparison.NONE : observationComparison;
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
        this(id, title, type, required, entry, preconditions, expected, steps, List.of(), "", TestObservationTrigger.NONE, TestObservationComparison.NONE);
    }

    public TestCaseSpec(
            String id,
            String title,
            String type,
            boolean required,
            String entry,
            String preconditions,
            String expected,
            List<TestStepSpec> steps,
            List<String> capabilities
    ) {
        this(id, title, type, required, entry, preconditions, expected, steps, capabilities, "", TestObservationTrigger.NONE, TestObservationComparison.NONE);
    }

    public boolean requiresObservationWindow() {
        return observationTrigger.requiresComparisonWindow() && observationComparison.requiresSnapshotComparison();
    }
}
