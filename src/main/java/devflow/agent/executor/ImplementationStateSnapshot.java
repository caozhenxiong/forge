package devflow.agent.executor;

import java.util.List;

record ImplementationStateSnapshot(
        String summary,
        List<PlannedSubtaskState> subtasks,
        List<SubtaskExecutionStateSnapshot> reports,
        List<EventState> events,
        String currentSubtaskTitle,
        boolean planCompleted,
        boolean architectCheckPassed,
        String architectFailureReason,
        String architectFailureDetails,
        List<String> incompleteSubtasks
) {

    ImplementationStateSnapshot(
            String summary,
            List<PlannedSubtaskState> subtasks,
            List<SubtaskExecutionStateSnapshot> reports,
            List<EventState> events,
            String currentSubtaskTitle,
            boolean planCompleted,
            boolean architectCheckPassed,
            List<String> incompleteSubtasks
    ) {
        this(
                summary,
                subtasks,
                reports,
                events,
                currentSubtaskTitle,
                planCompleted,
                architectCheckPassed,
                "",
                "",
                incompleteSubtasks
        );
    }

    record PlannedSubtaskState(
            String title,
            String goal,
            List<String> coverageRefs,
            List<String> ownedCapabilities,
            List<String> deferredCapabilities,
            List<String> acceptanceCriteria,
            boolean runnableMilestone,
            String deliveryMode,
            List<FileChangeState> changes
    ) {
    }

    record FileChangeState(
            String path,
            String action,
            String reason,
            String editScope,
            String runtimeOwnership
    ) {
        FileChangeState(String path, String action, String reason) {
            this(path, action, reason, FileEditScope.AUTO.name(), null);
        }

        FileChangeState(String path, String action, String reason, String editScope) {
            this(path, action, reason, editScope, null);
        }
    }

    record SubtaskExecutionStateSnapshot(
            String title,
            boolean completed,
            List<SubtaskAttemptState> attempts,
            String deliveryMode,
            boolean preferPreciseEditing,
            List<FilePatchProgressStateSnapshot> filePatchProgressStates
    ) {
        SubtaskExecutionStateSnapshot(
                String title,
                boolean completed,
                List<SubtaskAttemptState> attempts
        ) {
            this(title, completed, attempts, null, false, List.of());
        }
    }

    record FilePatchProgressStateSnapshot(
            String relativePath,
            String strategyName,
            String workingContent,
            List<EditUnitState> pendingUnits
    ) {
    }

    record EditUnitState(
            String kind,
            String label,
            List<String> allowedSymbols,
            int appendSymbolBudget,
            int splitDepth
    ) {
    }

    record SubtaskAttemptState(
            int attempt,
            boolean selfCheckPassed,
            String selfCheckSummary,
            String selfCheckDetails,
            String reviewDecision,
            String reviewFixMode,
            String reviewSummary,
            String reviewChangeRequest,
            String reviewEvidence,
            String reviewActionItems,
            GenerationFailureState generationFailure,
            RecoveryDecisionState recoveryDecision
    ) {
    }

    record GenerationFailureState(
            String failureType,
            String summary,
            String evidence,
            String retryHint
    ) {
    }

    record RecoveryDecisionState(
            String action,
            String mode,
            Integer maxFiles,
            Integer maxSymbols,
            boolean preferPreciseEditing,
            boolean forceBacklogSplit,
            boolean requireVerificationBeforeReview,
            String reason
    ) {
    }

    record EventState(
            String timestamp,
            String message
    ) {
    }
}
