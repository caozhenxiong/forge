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
            List<FileEditAttemptStateSnapshot> fileEditAttemptStates,
            List<FileChangeState> effectiveChanges,
            ToolLoopRuntimeStateSnapshot toolLoopRuntimeState
    ) {
        SubtaskExecutionStateSnapshot(
                String title,
                boolean completed,
                List<SubtaskAttemptState> attempts
        ) {
            this(title, completed, attempts, null, false, List.of(), List.of(), null);
        }

        SubtaskExecutionStateSnapshot(
                String title,
                boolean completed,
                List<SubtaskAttemptState> attempts,
                String deliveryMode,
                boolean preferPreciseEditing,
                List<FileEditAttemptStateSnapshot> fileEditAttemptStates
        ) {
            this(title, completed, attempts, deliveryMode, preferPreciseEditing, fileEditAttemptStates, List.of(), null);
        }

        SubtaskExecutionStateSnapshot(
                String title,
                boolean completed,
                List<SubtaskAttemptState> attempts,
                String deliveryMode,
                boolean preferPreciseEditing,
                List<FileEditAttemptStateSnapshot> fileEditAttemptStates,
                List<FileChangeState> effectiveChanges
        ) {
            this(title, completed, attempts, deliveryMode, preferPreciseEditing, fileEditAttemptStates, effectiveChanges, null);
        }
    }

    record ToolLoopRuntimeStateSnapshot(
            List<ChatMessageState> transcript,
            long readFileStateMaxEntries,
            long readFileStateMaxSizeBytes,
            List<ReadFileStateEntry> readFileStates,
            List<String> seenToolResultIds,
            List<ToolResultReplacementEntry> toolResultReplacements,
            List<FileMutationState> fileMutations
    ) {
    }

    record ChatMessageState(
            String role,
            String content,
            String toolName,
            String toolCallId,
            List<ToolCallState> toolCalls
    ) {
    }

    record ToolCallState(
            String id,
            String name,
            java.util.Map<String, Object> arguments
    ) {
    }

    record ReadFileStateEntry(
            String absolutePath,
            String content,
            long timestamp,
            Integer offset,
            Integer limit,
            boolean partialView
    ) {
    }

    record ToolResultReplacementEntry(
            String toolUseId,
            String replacement
    ) {
    }

    record FileMutationState(
            String operation,
            String relativePath,
            String beforeHash,
            String afterHash,
            List<StructuredPatchHunk> structuredPatch,
            long timestamp,
            String diagnosticStatus,
            String diagnosticEvidence
    ) {
    }

    record FileEditAttemptStateSnapshot(
            String relativePath,
            String protocolName,
            String strategyName,
            String workingContent,
            String plannedFromHash,
            List<String> completedTargetLabels,
            String currentTargetLabel
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
