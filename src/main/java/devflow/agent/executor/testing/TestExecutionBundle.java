package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public record TestExecutionBundle(
        String testCasesMarkdown,
        String runtimeSnapshotMarkdown,
        String executionMarkdown,
        String reportMarkdown
) {
}
