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
        String architectImplementationPatchTarget,
        String continuationMode,
        String continuationSummary,
        String continuationChangeRequest,
        String continuationEvidence,
        String continuationActionItems,
        String continuationPatchTarget,
        String continuationReasonCode,
        RuntimeContractState architectRuntimeContract,
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
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                null,
                incompleteSubtasks
        );
    }

    ImplementationStateSnapshot(
            String summary,
            List<PlannedSubtaskState> subtasks,
            List<SubtaskExecutionStateSnapshot> reports,
            List<EventState> events,
            String currentSubtaskTitle,
            boolean planCompleted,
            boolean architectCheckPassed,
            String architectFailureReason,
            String architectFailureDetails,
            String architectImplementationPatchTarget,
            RuntimeContractState architectRuntimeContract,
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
                architectFailureReason,
                architectFailureDetails,
                architectImplementationPatchTarget,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                architectRuntimeContract,
                incompleteSubtasks
        );
    }

    ImplementationStateSnapshot(
            String summary,
            List<PlannedSubtaskState> subtasks,
            List<SubtaskExecutionStateSnapshot> reports,
            List<EventState> events,
            String currentSubtaskTitle,
            boolean planCompleted,
            boolean architectCheckPassed,
            String architectFailureReason,
            String architectFailureDetails,
            String architectImplementationPatchTarget,
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
                architectFailureReason,
                architectFailureDetails,
                architectImplementationPatchTarget,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                null,
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
            String runtimeOwnership,
            boolean hostHtmlPatchRequired
    ) {
        FileChangeState(String path, String action, String reason) {
            this(path, action, reason, FileEditScope.AUTO.name(), null, false);
        }

        FileChangeState(String path, String action, String reason, String editScope) {
            this(path, action, reason, editScope, null, false);
        }

        FileChangeState(String path, String action, String reason, String editScope, String runtimeOwnership) {
            this(path, action, reason, editScope, runtimeOwnership, false);
        }
    }

    record RuntimeContractState(
            String htmlEntryPath,
            String runtimeOwnership,
            List<String> runtimePaths
    ) {
    }

    record SubtaskExecutionStateSnapshot(
            String title,
            boolean completed,
            List<SubtaskAttemptState> attempts,
            String deliveryMode,
            boolean preferPreciseEditing,
            List<FilePatchProgressStateSnapshot> filePatchProgressStates,
            List<FileChangeState> effectiveChanges
    ) {
        SubtaskExecutionStateSnapshot(
                String title,
                boolean completed,
                List<SubtaskAttemptState> attempts
        ) {
            this(title, completed, attempts, null, false, List.of(), List.of());
        }

        SubtaskExecutionStateSnapshot(
                String title,
                boolean completed,
                List<SubtaskAttemptState> attempts,
                String deliveryMode,
                boolean preferPreciseEditing,
                List<FilePatchProgressStateSnapshot> filePatchProgressStates
        ) {
            this(title, completed, attempts, deliveryMode, preferPreciseEditing, filePatchProgressStates, List.of());
        }
    }

    record FilePatchProgressStateSnapshot(
            String relativePath,
            String strategyName,
            String workingContent,
            String plannedFromHash,
            List<String> completedUnitLabels,
            String currentUnitLabel
    ) {
    }

    record SubtaskAttemptState(
            int attempt,
            boolean selfCheckPassed,
            String selfCheckSummary,
            String selfCheckDetails,
            List<ToolResultState> selfCheckToolResults,
            String reviewDecision,
            String reviewFixMode,
            String reviewSummary,
            String reviewChangeRequest,
            String reviewEvidence,
            String reviewActionItems,
            String reviewImplementationPatchTarget,
            GenerationFailureState generationFailure,
            RecoveryDecisionState recoveryDecision
    ) {
        SubtaskAttemptState(
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
                String reviewImplementationPatchTarget,
                GenerationFailureState generationFailure,
                RecoveryDecisionState recoveryDecision
        ) {
            this(
                    attempt,
                    selfCheckPassed,
                    selfCheckSummary,
                    selfCheckDetails,
                    List.of(),
                    reviewDecision,
                    reviewFixMode,
                    reviewSummary,
                    reviewChangeRequest,
                    reviewEvidence,
                    reviewActionItems,
                    reviewImplementationPatchTarget,
                    generationFailure,
                    recoveryDecision
            );
        }
    }

    record ToolResultState(
            String toolName,
            String status,
            String failureCode,
            String evidence,
            String recommendedNextAction
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
