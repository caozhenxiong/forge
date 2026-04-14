package devflow.agent.executor.implementation.state;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;
import devflow.agent.executor.tools.ToolStatus;

import devflow.agent.executor.generation.GenerationFailureReport;
import devflow.agent.executor.generation.GenerationFailureType;

import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.supervisor.DeliveryPolicyMode;
import devflow.agent.supervisor.GenerationRecoveryAction;
import devflow.agent.supervisor.GenerationRecoveryDecision;
import devflow.agent.util.EnumParsers;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import devflow.agent.executor.implementation.toolloop.ImplementationDiagnosticLedger;
import devflow.agent.executor.implementation.toolloop.ImplementationDiagnosticRecord;
import devflow.agent.executor.implementation.toolloop.ImplementationDiagnosticSource;
import devflow.agent.executor.implementation.toolloop.ImplementationToolSessionState;
import devflow.agent.executor.implementation.toolloop.ToolLoopDiagnosticStatus;
import devflow.agent.executor.implementation.toolloop.ToolLoopMutationOperation;
import devflow.agent.executor.implementation.toolloop.ToolLoopReadFileStateLedger;
import devflow.agent.executor.implementation.toolloop.ToolLoopResultReplacementState;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskAttemptReport;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
import devflow.agent.executor.subtask.SubtaskExecutionState;
/**
 * implementation 快照恢复器。
 *
 * <p>负责把 `ImplementationStateSnapshot` 里的计划、报告和 attempt
 * 还原成运行时对象，避免 resume policy 继续兼顾快照解析细节。
 */
public final class ImplementationSnapshotRestorer {

    public List<Subtask> restoreSubtasks(List<ImplementationStateSnapshot.PlannedSubtaskState> subtasks) {
        if (subtasks == null || subtasks.isEmpty()) {
            return List.of();
        }
        List<Subtask> restored = new ArrayList<>();
        for (ImplementationStateSnapshot.PlannedSubtaskState subtask : subtasks) {
            if (subtask == null) {
                continue;
            }
            DeliveryMode deliveryMode = parseDeliveryMode(subtask.deliveryMode(), DeliveryMode.INCREMENTAL);
            List<FileChange> changes = (subtask.changes() == null ? List.<ImplementationStateSnapshot.FileChangeState>of() : subtask.changes()).stream()
                    .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                    .map(change -> new FileChange(
                            change.path(),
                            parseChangeAction(change.action(), ChangeAction.WRITE),
                            change.reason(),
                            EnumParsers.parseIgnoreCase(FileEditScope.class, change.editScope(), FileEditScope.AUTO),
                            EnumParsers.parseIgnoreCase(RuntimeOwnershipMode.class, change.runtimeOwnership(), null),
                            change.hostHtmlPatchRequired()
                    ))
                    .toList();
            restored.add(new Subtask(
                    blankIfNull(subtask.title()),
                    blankIfNull(subtask.goal()),
                    safeList(subtask.coverageRefs()),
                    safeList(subtask.ownedCapabilities()),
                    safeList(subtask.deferredCapabilities()),
                    safeList(subtask.acceptanceCriteria()),
                    subtask.runnableMilestone(),
                    deliveryMode,
                    changes
            ));
        }
        return restored;
    }

    public List<SubtaskExecutionReport> restoreReports(
            List<ImplementationStateSnapshot.SubtaskExecutionStateSnapshot> reports,
            List<Subtask> subtasks
    ) {
        if (reports == null || reports.isEmpty() || subtasks == null || subtasks.isEmpty()) {
            return List.of();
        }
        List<SubtaskExecutionReport> restored = new ArrayList<>();
        int limit = Math.min(reports.size(), subtasks.size());
        for (int index = 0; index < limit; index++) {
            ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report = reports.get(index);
            Subtask subtask = subtasks.get(index);
            if (report == null || subtask == null) {
                continue;
            }
            List<SubtaskAttemptReport> attempts = (report.attempts() == null ? List.<ImplementationStateSnapshot.SubtaskAttemptState>of() : report.attempts()).stream()
                    .map(this::restoreAttempt)
                    .toList();
            restored.add(new SubtaskExecutionReport(
                    subtask,
                    report.completed(),
                    attempts,
                    restoreExecutionState(report)
            ));
        }
        return restored;
    }

    public HtmlRuntimeOwnershipContract restoreRuntimeContract(ImplementationStateSnapshot.RuntimeContractState runtimeContractState) {
        if (runtimeContractState == null
                || runtimeContractState.htmlEntryPath() == null
                || runtimeContractState.htmlEntryPath().isBlank()) {
            return null;
        }
        RuntimeOwnershipMode runtimeOwnership = EnumParsers.parseIgnoreCase(
                RuntimeOwnershipMode.class,
                runtimeContractState.runtimeOwnership(),
                null
        );
        if (runtimeOwnership == null) {
            return null;
        }
        List<Path> runtimePaths = safeList(runtimeContractState.runtimePaths()).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(Path::of)
                .map(Path::normalize)
                .toList();
        return new HtmlRuntimeOwnershipContract(
                Path.of(runtimeContractState.htmlEntryPath()).normalize(),
                runtimeOwnership,
                runtimePaths
        );
    }

    public ArchitectIntegrationCheckResult restoreContractGate(ImplementationStateSnapshot.ContractGateState contractGateState) {
        if (contractGateState == null) {
            return null;
        }
        ArchitectIntegrationCheckScope scope = EnumParsers.parseIgnoreCase(
                ArchitectIntegrationCheckScope.class,
                contractGateState.scope(),
                ArchitectIntegrationCheckScope.STAGE_COMPLETION
        );
        if (contractGateState.passed()) {
            return ArchitectIntegrationCheckResult.success(scope);
        }
        return ArchitectIntegrationCheckResult.failure(
                scope,
                EnumParsers.parseIgnoreCase(
                        ArchitectIntegrationFailureReason.class,
                        contractGateState.failureReason(),
                        ArchitectIntegrationFailureReason.IMPLEMENTATION_INCOMPLETE
                ),
                blankIfNull(contractGateState.details()),
                EnumParsers.parseIgnoreCase(
                        ImplementationPatchTarget.class,
                        contractGateState.implementationPatchTarget(),
                        ImplementationPatchTarget.NONE
                ),
                restoreRuntimeContract(contractGateState.runtimeContract())
        );
    }

    public List<SubtaskExecutionReport> takeCompletedPrefix(List<SubtaskExecutionReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        List<SubtaskExecutionReport> prefix = new ArrayList<>();
        for (SubtaskExecutionReport report : reports) {
            if (report == null || !report.completed()) {
                break;
            }
            prefix.add(report);
        }
        return prefix;
    }

    public SubtaskExecutionState restoreExecutionState(SubtaskExecutionReport report) {
        return report == null || report.executionState() == null ? null : report.executionState().copy();
    }

    private SubtaskExecutionState restoreExecutionState(ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report) {
        if (report == null || report.deliveryMode() == null || report.deliveryMode().isBlank()) {
            return null;
        }
        return SubtaskExecutionState.restore(
                report.deliveryMode(),
                report.preferPreciseEditing(),
                restoreFileEditAttemptStates(report.fileEditAttemptStates()),
                restoreEffectiveChanges(report.effectiveChanges()),
                restoreToolSessionState(report.toolSessionState())
        );
    }

    private List<FileEditAttemptState> restoreFileEditAttemptStates(
            List<ImplementationStateSnapshot.FileEditAttemptStateSnapshot> snapshots
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<FileEditAttemptState> restored = new ArrayList<>();
        for (ImplementationStateSnapshot.FileEditAttemptStateSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.relativePath() == null || snapshot.relativePath().isBlank()) {
                continue;
            }
            restored.add(new FileEditAttemptState(
                    Path.of(snapshot.relativePath()).normalize(),
                    blankIfNull(snapshot.protocolName()),
                    blankIfNull(snapshot.strategyName()),
                    blankIfNull(snapshot.workingContent()),
                    blankIfNull(snapshot.plannedFromHash()),
                    safeList(snapshot.completedTargetLabels()),
                    blankIfNull(snapshot.currentTargetLabel())
            ));
        }
        return restored;
    }

    private List<FileChange> restoreEffectiveChanges(List<ImplementationStateSnapshot.FileChangeState> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<FileChange> restored = new ArrayList<>();
        for (ImplementationStateSnapshot.FileChangeState snapshot : snapshots) {
            if (snapshot == null || snapshot.path() == null || snapshot.path().isBlank()) {
                continue;
            }
            restored.add(new FileChange(
                    snapshot.path(),
                    parseChangeAction(snapshot.action(), ChangeAction.WRITE),
                    blankIfNull(snapshot.reason()),
                    EnumParsers.parseIgnoreCase(FileEditScope.class, snapshot.editScope(), FileEditScope.AUTO),
                    EnumParsers.parseIgnoreCase(RuntimeOwnershipMode.class, snapshot.runtimeOwnership(), null),
                    snapshot.hostHtmlPatchRequired()
            ));
        }
        return restored;
    }

    private SubtaskAttemptReport restoreAttempt(ImplementationStateSnapshot.SubtaskAttemptState attempt) {
        if (attempt == null) {
            return new SubtaskAttemptReport(
                    1,
                    new SelfCheckResult(false, "", ""),
                    List.of(),
                    new ReviewResult(
                            ReviewDecision.REVISION_REQUIRED,
                            FixMode.PATCH,
                            "",
                            "",
                            "",
                            "",
                            ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                    ),
                    null,
                    null
            );
        }
        SelfCheckResult selfCheck = new SelfCheckResult(
                attempt.selfCheckPassed(),
                blankIfNull(attempt.selfCheckSummary()),
                blankIfNull(attempt.selfCheckDetails())
        );
        List<ToolResult> selfCheckToolResults = restoreToolResults(attempt.selfCheckToolResults());
        ReviewResult review = new ReviewResult(
                parseReviewDecision(attempt.reviewDecision(), ReviewDecision.REVISION_REQUIRED),
                parseFixMode(attempt.reviewFixMode(), FixMode.PATCH),
                blankIfNull(attempt.reviewSummary()),
                blankIfNull(attempt.reviewChangeRequest()),
                blankIfNull(attempt.reviewEvidence()),
                blankIfNull(attempt.reviewActionItems()),
                EnumParsers.parseIgnoreCase(
                        ImplementationPatchTarget.class,
                        attempt.reviewImplementationPatchTarget(),
                        ImplementationPatchTarget.NONE
                )
        );
        GenerationFailureReport generationFailure = null;
        if (attempt.generationFailure() != null) {
            ImplementationStateSnapshot.GenerationFailureState failure = attempt.generationFailure();
            generationFailure = new GenerationFailureReport(
                    "",
                    "",
                    "",
                    parseGenerationFailureType(failure.failureType(), GenerationFailureType.VALIDATION_FAILED),
                    0,
                    true,
                    blankIfNull(failure.summary()),
                    blankIfNull(failure.evidence()),
                    blankIfNull(failure.retryHint())
            );
        }
        GenerationRecoveryDecision recoveryDecision = null;
        if (attempt.recoveryDecision() != null) {
            ImplementationStateSnapshot.RecoveryDecisionState recovery = attempt.recoveryDecision();
            recoveryDecision = new GenerationRecoveryDecision(
                    parseGenerationRecoveryAction(recovery.action(), GenerationRecoveryAction.FAIL_SUBTASK),
                    new DeliveryPolicy(
                            DeliveryPolicyMode.fromWireValue(blankIfNull(recovery.mode()), DeliveryPolicyMode.PATCH),
                            recovery.maxFiles(),
                            recovery.maxSymbols(),
                            recovery.preferPreciseEditing(),
                            recovery.forceBacklogSplit(),
                            recovery.requireVerificationBeforeReview()
                    ),
                    blankIfNull(recovery.reason()),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
        return new SubtaskAttemptReport(
                attempt.attempt(),
                selfCheck,
                selfCheckToolResults,
                review,
                generationFailure,
                recoveryDecision
        );
    }

    private List<ToolResult> restoreToolResults(List<ImplementationStateSnapshot.ToolResultState> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return List.of();
        }
        List<ToolResult> restored = new ArrayList<>();
        for (ImplementationStateSnapshot.ToolResultState toolResult : toolResults) {
            if (toolResult == null) {
                continue;
            }
            ToolName toolName = EnumParsers.parseIgnoreCase(ToolName.class, toolResult.toolName(), null);
            ToolStatus toolStatus = EnumParsers.parseIgnoreCase(ToolStatus.class, toolResult.status(), null);
            if (toolName == null || toolStatus == null) {
                continue;
            }
            restored.add(new ToolResult(
                    toolName,
                    toolStatus,
                    EnumParsers.parseIgnoreCase(ToolFailureCode.class, toolResult.failureCode(), null),
                    blankIfNull(toolResult.evidence()),
                    blankIfNull(toolResult.recommendedNextAction())
            ));
        }
        return List.copyOf(restored);
    }

    private ImplementationToolSessionState restoreToolSessionState(
            ImplementationStateSnapshot.ToolSessionStateSnapshot snapshot
    ) {
        if (snapshot == null) {
            return new ImplementationToolSessionState();
        }
        ToolLoopReadFileStateLedger ledger = new ToolLoopReadFileStateLedger(
                snapshot.readFileStateMaxEntries() <= 0 ? 100L : snapshot.readFileStateMaxEntries(),
                snapshot.readFileStateMaxSizeBytes() <= 0 ? 25L * 1024L * 1024L : snapshot.readFileStateMaxSizeBytes(),
                snapshot.readFileStates()
        );
        ToolLoopResultReplacementState replacementState = new ToolLoopResultReplacementState(
                snapshot.seenToolResultIds(),
                snapshot.toolResultReplacements()
        );
        return new ImplementationToolSessionState(
                List.of(),
                ledger,
                replacementState,
                restoreMutations(snapshot.fileMutations()),
                restoreDiagnostics(snapshot.diagnostics())
        );
    }

    private List<FileMutationRecord> restoreMutations(List<ImplementationStateSnapshot.FileMutationState> fileMutations) {
        if (fileMutations == null || fileMutations.isEmpty()) {
            return List.of();
        }
        List<FileMutationRecord> restored = new ArrayList<>();
        for (ImplementationStateSnapshot.FileMutationState mutation : fileMutations) {
            if (mutation == null || mutation.relativePath() == null || mutation.relativePath().isBlank()) {
                continue;
            }
            ToolLoopMutationOperation operation = EnumParsers.parseIgnoreCase(
                    ToolLoopMutationOperation.class,
                    mutation.operation(),
                    null
            );
            if (operation == null) {
                continue;
            }
            restored.add(new FileMutationRecord(
                    operation,
                    Path.of(mutation.relativePath()).normalize(),
                    mutation.beforeExists(),
                    blankIfNull(mutation.beforeHash()),
                    mutation.afterExists(),
                    blankIfNull(mutation.afterHash()),
                    mutation.structuredPatch(),
                    mutation.timestamp()
            ));
        }
        return List.copyOf(restored);
    }

    private ImplementationDiagnosticLedger restoreDiagnostics(List<ImplementationStateSnapshot.DiagnosticState> diagnostics) {
        ImplementationDiagnosticLedger ledger = new ImplementationDiagnosticLedger();
        if (diagnostics == null || diagnostics.isEmpty()) {
            return ledger;
        }
        for (ImplementationStateSnapshot.DiagnosticState diagnostic : diagnostics) {
            if (diagnostic == null || diagnostic.relativePath() == null || diagnostic.relativePath().isBlank()) {
                continue;
            }
            ledger.restore(new ImplementationDiagnosticRecord(
                    blankIfNull(diagnostic.diagnosticId()),
                    Path.of(diagnostic.relativePath()).normalize(),
                    EnumParsers.parseIgnoreCase(
                            ToolLoopDiagnosticStatus.class,
                            diagnostic.status(),
                            ToolLoopDiagnosticStatus.UNSUPPORTED
                    ),
                    EnumParsers.parseIgnoreCase(
                            ImplementationDiagnosticSource.class,
                            diagnostic.source(),
                            ImplementationDiagnosticSource.UNSUPPORTED_LANGUAGE
                    ),
                    blankIfNull(diagnostic.evidence()),
                    diagnostic.timestamp()
            ));
        }
        return ledger;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private DeliveryMode parseDeliveryMode(String value, DeliveryMode fallback) {
        return EnumParsers.parseIgnoreCase(DeliveryMode.class, value, fallback);
    }

    private ChangeAction parseChangeAction(String value, ChangeAction fallback) {
        return EnumParsers.parseIgnoreCase(ChangeAction.class, value, fallback);
    }

    private ReviewDecision parseReviewDecision(String value, ReviewDecision fallback) {
        return EnumParsers.parseIgnoreCase(ReviewDecision.class, value, fallback);
    }

    private FixMode parseFixMode(String value, FixMode fallback) {
        return EnumParsers.parseIgnoreCase(FixMode.class, value, fallback);
    }

    private GenerationFailureType parseGenerationFailureType(String value, GenerationFailureType fallback) {
        return EnumParsers.parseIgnoreCase(GenerationFailureType.class, value, fallback);
    }

    private GenerationRecoveryAction parseGenerationRecoveryAction(String value, GenerationRecoveryAction fallback) {
        return EnumParsers.parseIgnoreCase(GenerationRecoveryAction.class, value, fallback);
    }
}
