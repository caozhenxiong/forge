package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * 负责把 runtime snapshot 投影成 implementation_state.json。
 *
 * <p>这层只做结构化状态序列化，不参与 markdown 渲染，
 * 这样运行时状态 JSON 和进度/事件视图可以各自演进。
 */
final class ImplementationStateSnapshotSerializer {

    private final ImplementationStateCodec stateCodec;

    ImplementationStateSnapshotSerializer(ObjectMapper objectMapper) {
        this.stateCodec = new ImplementationStateCodec(objectMapper);
    }

    String renderStateJson(ImplementationRuntimeSnapshot runtimeSnapshot) {
        try {
            List<ImplementationStateSnapshot.PlannedSubtaskState> subtasks =
                    serializeSubtasks(runtimeSnapshot.plan() == null ? List.of() : runtimeSnapshot.plan().subtasks());
            List<ImplementationStateSnapshot.SubtaskExecutionStateSnapshot> reports = serializeReports(runtimeSnapshot.reports());
            List<ImplementationStateSnapshot.EventState> events = serializeEvents(runtimeSnapshot.events());
            ImplementationStageStatus stageStatus = runtimeSnapshot.stageStatus();
            ImplementationStateSnapshot snapshot = new ImplementationStateSnapshot(
                    runtimeSnapshot.plan() == null ? "" : ImplementationArtifactRenderSupport.blankIfNull(runtimeSnapshot.plan().summary()),
                    subtasks,
                    reports,
                    events,
                    ImplementationArtifactRenderSupport.blankIfNull(runtimeSnapshot.currentSubtaskTitle()),
                    stageStatus != null && stageStatus.planCompleted(),
                    stageStatus != null && stageStatus.stageReady(),
                    serializeContractGate(stageStatus == null ? null : stageStatus.contractGateResult()),
                    stageStatus == null ? "" : stageStatus.continuationMode().name(),
                    stageStatus == null ? "" : stageStatus.continuationSummary(),
                    stageStatus == null ? "" : stageStatus.continuationChangeRequest(),
                    stageStatus == null ? "" : stageStatus.continuationEvidence(),
                    stageStatus == null ? "" : stageStatus.continuationActionItems(),
                    stageStatus == null
                            ? List.of()
                            : stageStatus.continuationOverrideChanges().stream()
                                    .map(change -> new ImplementationStateSnapshot.FileChangeState(
                                            change.path(),
                                            change.action().name(),
                                            change.reason(),
                                            change.effectiveEditScope().name(),
                                            change.runtimeOwnership() == null ? null : change.runtimeOwnership().name(),
                                            change.hostHtmlPatchRequired()
                                    ))
                                    .toList(),
                    stageStatus == null ? "" : stageStatus.continuationPatchTarget().name(),
                    stageStatus == null ? "" : stageStatus.continuationReasonCode().name(),
                    stageStatus == null ? List.of() : stageStatus.incompleteSubtasks()
            );
            return stateCodec.write(snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render implementation state: " + exception.getMessage(), exception);
        }
    }

    private ImplementationStateSnapshot.ContractGateState serializeContractGate(
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        if (contractGateResult == null) {
            return null;
        }
        return new ImplementationStateSnapshot.ContractGateState(
                contractGateResult.scope() == null ? null : contractGateResult.scope().name(),
                contractGateResult.passed(),
                contractGateResult.failureReason() == null ? null : contractGateResult.failureReason().name(),
                ImplementationArtifactRenderSupport.blankIfNull(contractGateResult.details()),
                contractGateResult.implementationPatchTarget() == null
                        ? null
                        : contractGateResult.implementationPatchTarget().name(),
                serializeRuntimeContract(contractGateResult.runtimeContract())
        );
    }

    private ImplementationStateSnapshot.RuntimeContractState serializeRuntimeContract(
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        if (runtimeContract == null || !runtimeContract.active()) {
            return null;
        }
        return new ImplementationStateSnapshot.RuntimeContractState(
                runtimeContract.htmlEntryPath() == null ? null : runtimeContract.htmlEntryPath().toString(),
                runtimeContract.runtimeOwnership() == null ? null : runtimeContract.runtimeOwnership().name(),
                runtimeContract.runtimePaths().stream().map(path -> path.toString().replace('\\', '/')).toList()
        );
    }

    private List<ImplementationStateSnapshot.EventState> serializeEvents(List<ImplementationEventEntry> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream()
                .map(event -> new ImplementationStateSnapshot.EventState(
                        event.timestamp() == null ? "" : event.timestamp().toString(),
                        ImplementationArtifactRenderSupport.blankIfNull(event.message())
                ))
                .toList();
    }

    private List<ImplementationStateSnapshot.PlannedSubtaskState> serializeSubtasks(List<Subtask> subtasks) {
        if (subtasks == null || subtasks.isEmpty()) {
            return List.of();
        }
        return subtasks.stream()
                .map(subtask -> new ImplementationStateSnapshot.PlannedSubtaskState(
                        subtask.title(),
                        subtask.goal(),
                        ImplementationArtifactRenderSupport.safeList(subtask.coverageRefs()),
                        ImplementationArtifactRenderSupport.safeList(subtask.ownedCapabilities()),
                        ImplementationArtifactRenderSupport.safeList(subtask.deferredCapabilities()),
                        ImplementationArtifactRenderSupport.safeList(subtask.acceptanceCriteria()),
                        subtask.runnableMilestone(),
                        subtask.deliveryMode().name(),
                        (subtask.changes() == null ? List.<FileChange>of() : subtask.changes()).stream()
                                .map(change -> new ImplementationStateSnapshot.FileChangeState(
                                        change.path(),
                                        change.action().name(),
                                        change.reason(),
                                        change.effectiveEditScope().name(),
                                        change.runtimeOwnership() == null ? null : change.runtimeOwnership().name(),
                                        change.hostHtmlPatchRequired()
                                ))
                                .toList()
                ))
                .toList();
    }

    private List<ImplementationStateSnapshot.SubtaskExecutionStateSnapshot> serializeReports(List<SubtaskExecutionReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        return reports.stream()
                .filter(report -> report != null)
                .map(report -> new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                        report.subtask().title(),
                        report.completed(),
                        (report.attempts() == null ? List.<SubtaskAttemptReport>of() : report.attempts()).stream()
                                .map(this::serializeAttempt)
                                .toList(),
                        report.executionState() == null ? null : report.executionState().deliveryMode().name(),
                        report.executionState() != null && report.executionState().preferPreciseEditing(),
                        serializeFileEditAttemptStates(report.executionState()),
                        serializeEffectiveChanges(report.executionState()),
                        serializeToolSessionState(report.executionState())
                ))
                .toList();
    }

    private List<ImplementationStateSnapshot.FileEditAttemptStateSnapshot> serializeFileEditAttemptStates(
            SubtaskExecutionState executionState
    ) {
        if (executionState == null) {
            return List.of();
        }
        return executionState.fileEditAttemptStates().stream()
                .map(progressState -> new ImplementationStateSnapshot.FileEditAttemptStateSnapshot(
                        progressState.relativePath() == null ? "" : progressState.relativePath().toString(),
                        progressState.protocolName(),
                        progressState.strategyName(),
                        progressState.workingContent(),
                        progressState.plannedFromHash(),
                        progressState.completedTargetLabels(),
                        progressState.currentTargetLabel()
                ))
                .toList();
    }

    private List<ImplementationStateSnapshot.FileChangeState> serializeEffectiveChanges(
            SubtaskExecutionState executionState
    ) {
        if (executionState == null) {
            return List.of();
        }
        return executionState.effectiveChanges().stream()
                .map(change -> new ImplementationStateSnapshot.FileChangeState(
                        change.path(),
                        change.action().name(),
                        change.reason(),
                        change.effectiveEditScope().name(),
                        change.runtimeOwnership() == null ? null : change.runtimeOwnership().name(),
                        change.hostHtmlPatchRequired()
                ))
                .toList();
    }

    private ImplementationStateSnapshot.ToolSessionStateSnapshot serializeToolSessionState(
            SubtaskExecutionState executionState
    ) {
        if (executionState == null || executionState.toolSessionState() == null) {
            return null;
        }
        ImplementationToolSessionState toolSessionState = executionState.toolSessionState();
        return new ImplementationStateSnapshot.ToolSessionStateSnapshot(
                toolSessionState.readFileStateLedger().maxEntries(),
                toolSessionState.readFileStateLedger().maxSizeBytes(),
                toolSessionState.readFileStateLedger().snapshotEntries(),
                toolSessionState.resultReplacementState().snapshotSeenIds(),
                toolSessionState.resultReplacementState().snapshotReplacements(),
                toolSessionState.mutationRecords().stream()
                        .map(mutation -> new ImplementationStateSnapshot.FileMutationState(
                                mutation.operation().name(),
                                mutation.relativePath() == null ? "" : mutation.relativePath().toString(),
                                mutation.beforeExists(),
                                mutation.beforeHash(),
                                mutation.afterExists(),
                                mutation.afterHash(),
                                mutation.structuredPatch(),
                                mutation.timestamp()
                        ))
                        .toList(),
                toolSessionState.diagnostics().stream()
                        .map(diagnostic -> new ImplementationStateSnapshot.DiagnosticState(
                                diagnostic.diagnosticId(),
                                diagnostic.relativePath() == null ? "" : diagnostic.relativePath().toString(),
                                diagnostic.status().name(),
                                diagnostic.source().name(),
                                diagnostic.evidence(),
                                diagnostic.timestamp()
                        ))
                        .toList()
        );
    }

    private ImplementationStateSnapshot.SubtaskAttemptState serializeAttempt(SubtaskAttemptReport attempt) {
        ImplementationStateSnapshot.GenerationFailureState failureState = attempt.generationFailure() == null
                ? null
                : new ImplementationStateSnapshot.GenerationFailureState(
                        attempt.generationFailure().failureType().name(),
                        attempt.generationFailure().summary(),
                        attempt.generationFailure().evidence(),
                        attempt.generationFailure().retryHint()
                );
        ImplementationStateSnapshot.RecoveryDecisionState recoveryState = attempt.recoveryDecision() == null
                ? null
                : new ImplementationStateSnapshot.RecoveryDecisionState(
                        attempt.recoveryDecision().action().name(),
                        attempt.recoveryDecision().deliveryPolicy().mode() == null
                                ? null
                                : attempt.recoveryDecision().deliveryPolicy().mode().wireValue(),
                        attempt.recoveryDecision().deliveryPolicy().maxFiles(),
                        attempt.recoveryDecision().deliveryPolicy().maxSymbols(),
                        attempt.recoveryDecision().deliveryPolicy().preferPreciseEditing(),
                        attempt.recoveryDecision().deliveryPolicy().forceBacklogSplit(),
                        attempt.recoveryDecision().deliveryPolicy().requireVerificationBeforeReview(),
                        attempt.recoveryDecision().reason()
                );
        return new ImplementationStateSnapshot.SubtaskAttemptState(
                attempt.attempt(),
                attempt.selfCheck().passed(),
                attempt.selfCheck().summary(),
                attempt.selfCheck().details(),
                serializeToolResults(attempt.selfCheckToolResults()),
                attempt.review().decision().name(),
                attempt.review().fixMode().name(),
                attempt.review().summary(),
                attempt.review().changeRequest(),
                attempt.review().evidence(),
                attempt.review().actionItems(),
                attempt.review().implementationPatchTarget() == null
                        ? null
                        : attempt.review().implementationPatchTarget().name(),
                failureState,
                recoveryState
        );
    }

    private List<ImplementationStateSnapshot.ToolResultState> serializeToolResults(List<ToolResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return List.of();
        }
        return toolResults.stream()
                .filter(toolResult -> toolResult != null)
                .map(toolResult -> new ImplementationStateSnapshot.ToolResultState(
                        toolResult.toolName() == null ? null : toolResult.toolName().name(),
                        toolResult.status() == null ? null : toolResult.status().name(),
                        toolResult.failureCode() == null ? null : toolResult.failureCode().name(),
                        toolResult.evidence(),
                        toolResult.recommendedNextAction()
                ))
                .toList();
    }
}
