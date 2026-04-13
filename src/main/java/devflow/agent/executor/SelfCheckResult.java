package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public record SelfCheckResult(
        boolean passed,
        String summary,
        String details
) {
}
