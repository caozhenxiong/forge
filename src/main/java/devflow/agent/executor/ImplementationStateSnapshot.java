package devflow.agent.executor;

import java.util.List;

record ImplementationStateSnapshot(
        String summary,
        List<PlannedSubtaskState> subtasks,
        List<SubtaskExecutionStateSnapshot> reports,
        List<EventState> events,
        String currentSubtaskTitle,
        boolean planCompleted,
        boolean stageReady,
        ContractGateState contractGate,
        String continuationMode,
        String continuationSummary,
        String continuationChangeRequest,
        String continuationEvidence,
        String continuationActionItems,
        List<FileChangeState> continuationOverrideChanges,
        String continuationPatchTarget,
        String continuationReasonCode,
        List<String> incompleteSubtasks
) {

    ImplementationStateSnapshot {
        summary = summary == null ? "" : summary;
        subtasks = subtasks == null ? List.of() : List.copyOf(subtasks);
        reports = reports == null ? List.of() : List.copyOf(reports);
        events = events == null ? List.of() : List.copyOf(events);
        currentSubtaskTitle = currentSubtaskTitle == null ? "" : currentSubtaskTitle;
        continuationMode = continuationMode == null ? "" : continuationMode;
        continuationSummary = continuationSummary == null ? "" : continuationSummary;
        continuationChangeRequest = continuationChangeRequest == null ? "" : continuationChangeRequest;
        continuationEvidence = continuationEvidence == null ? "" : continuationEvidence;
        continuationActionItems = continuationActionItems == null ? "" : continuationActionItems;
        continuationOverrideChanges = continuationOverrideChanges == null ? List.of() : List.copyOf(continuationOverrideChanges);
        continuationPatchTarget = continuationPatchTarget == null ? "" : continuationPatchTarget;
        continuationReasonCode = continuationReasonCode == null ? "" : continuationReasonCode;
        incompleteSubtasks = incompleteSubtasks == null ? List.of() : List.copyOf(incompleteSubtasks);
    }

    ImplementationStateSnapshot(
            String summary,
            List<PlannedSubtaskState> subtasks,
            List<SubtaskExecutionStateSnapshot> reports,
            List<EventState> events,
            String currentSubtaskTitle,
            boolean planCompleted,
            boolean stageReady,
            List<String> incompleteSubtasks
    ) {
        this(
                summary,
                subtasks,
                reports,
                events,
                currentSubtaskTitle,
                planCompleted,
                stageReady,
                null,
                "",
                "",
                "",
                "",
                "",
                List.of(),
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

    record ContractGateState(
            String scope,
            boolean passed,
            String failureReason,
            String details,
            String implementationPatchTarget,
            RuntimeContractState runtimeContract
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
            ToolSessionStateSnapshot toolSessionState
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

    record ToolSessionStateSnapshot(
            long readFileStateMaxEntries,
            long readFileStateMaxSizeBytes,
            List<ReadFileStateEntry> readFileStates,
            List<String> seenToolResultIds,
            List<ToolResultReplacementEntry> toolResultReplacements,
            List<FileMutationState> fileMutations,
            List<DiagnosticState> diagnostics
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
            boolean beforeExists,
            String beforeHash,
            boolean afterExists,
            String afterHash,
            List<StructuredPatchHunk> structuredPatch,
            long timestamp
    ) {
    }

    record DiagnosticState(
            String diagnosticId,
            String relativePath,
            String status,
            String source,
            String evidence,
            long timestamp
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
