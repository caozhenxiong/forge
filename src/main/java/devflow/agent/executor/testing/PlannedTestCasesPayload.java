package devflow.agent.executor.testing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record PlannedTestCasesPayload(
        @JsonProperty("summary") String summary,
        @JsonProperty("cases") List<PlannedTestCasePayload> cases
) {
}

record PlannedTestCasePayload(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("type") String type,
        @JsonProperty("required") Boolean required,
        @JsonProperty("entry") String entry,
        @JsonProperty("preconditions") String preconditions,
        @JsonProperty("expected") String expected,
        @JsonProperty("capabilities") List<String> capabilities,
        @JsonProperty("observationTargetId") String observationTargetId,
        @JsonProperty("observationTrigger") String observationTrigger,
        @JsonProperty("observationComparison") String observationComparison,
        @JsonProperty("steps") List<PlannedTestStepPayload> steps
) {
    PlannedTestCasePayload(
            String id,
            String title,
            String type,
            Boolean required,
            String entry,
            String preconditions,
            String expected,
            List<String> capabilities,
            List<PlannedTestStepPayload> steps
    ) {
        this(id, title, type, required, entry, preconditions, expected, capabilities, "", null, null, steps);
    }

    PlannedTestCasePayload(
            String id,
            String title,
            String type,
            Boolean required,
            String entry,
            String preconditions,
            String expected,
            List<PlannedTestStepPayload> steps
    ) {
        this(id, title, type, required, entry, preconditions, expected, List.of(), "", null, null, steps);
    }
}

record PlannedTestStepPayload(
        @JsonProperty("action") String action,
        @JsonProperty("selector") String selector,
        @JsonProperty("key") String key,
        @JsonProperty("count") Integer count,
        @JsonProperty("ms") Integer ms,
        @JsonProperty("text") String text,
        @JsonProperty("optional") Boolean optional,
        @JsonProperty("semantic") String semantic
) {
    PlannedTestStepPayload(
            String action,
            String selector,
            String key,
            Integer count,
            Integer ms,
            String text,
            Boolean optional
    ) {
        this(action, selector, key, count, ms, text, optional, null);
    }
}
