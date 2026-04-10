package devflow.agent.validation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record ValidationPlanningPayload(
        @JsonProperty("summary") String summary,
        @JsonProperty("steps") List<ValidationPlannedStep> steps
) {
}
