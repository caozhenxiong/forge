package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public record ImplementationExecutionBundle(
        String implementationMarkdown,
        String backlogMarkdown,
        String repairAlignmentMarkdown,
        String sharedContextMarkdown,
        String taskPackagesMarkdown,
        String workerResultsMarkdown,
        String eventsMarkdown,
        String diagnosticsMarkdown,
        String progressMarkdown,
        String stageStatusMarkdown,
        String stateJson,
        ImplementationRuntimeSnapshot snapshot
) {
}
