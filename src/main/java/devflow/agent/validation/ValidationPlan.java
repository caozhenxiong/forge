package devflow.agent.validation;

import java.util.List;

public record ValidationPlan(
        String summary,
        List<ValidationStep> steps
) {
}
