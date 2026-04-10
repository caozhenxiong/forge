package devflow.agent.validation;

import com.fasterxml.jackson.annotation.JsonProperty;

record ValidationPlannedStep(
        @JsonProperty("capability") String capability,
        @JsonProperty("reason") String reason,
        @JsonProperty("required") Boolean required
) {
}
