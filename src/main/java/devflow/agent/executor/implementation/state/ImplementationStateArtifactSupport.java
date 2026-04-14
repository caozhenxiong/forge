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

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.protocol.FileChangePayload;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import java.util.ArrayList;
import java.util.List;

import devflow.agent.executor.subtask.Subtask;
/**
 * implementation_state.json 的单一读取入口。
 *
 * <p>implementation 的机器控制流只能从这一份快照恢复 live 状态；
 * stage-status / worker-results / diagnostics markdown 只是派生展示物，
 * 不再参与 continuation、review intake 或阶段推进判定。
 */
public final class ImplementationStateArtifactSupport {

    private final ImplementationStateCodec stateCodec;

    public ImplementationStateArtifactSupport() {
        this.stateCodec = new ImplementationStateCodec(null);
    }

    public ImplementationStageStatusPayload readStageStatus(String stateJson) {
        ImplementationStateSnapshot snapshot = requireSnapshot(stateJson);
        return new ImplementationStageStatusPayload(
                snapshot.stageReady(),
                snapshot.planCompleted(),
                safeList(snapshot.incompleteSubtasks()),
                toContractGatePayload(snapshot.contractGate()),
                continuationMode(snapshot),
                blank(snapshot.continuationSummary()),
                blank(snapshot.continuationChangeRequest()),
                blank(snapshot.continuationEvidence()),
                blank(snapshot.continuationActionItems()),
                toFileChangePayloads(snapshot.continuationOverrideChanges()),
                patchTarget(snapshot.continuationPatchTarget()),
                reasonCode(snapshot.continuationReasonCode())
        );
    }

    public String renderReviewSummary(String stateJson) {
        ImplementationStateSnapshot snapshot = requireSnapshot(stateJson);
        StringBuilder builder = new StringBuilder("""
                # 实现状态摘要

                - stageReady: %s
                - planCompleted: %s
                - currentSubtask: %s
                - incompleteSubtasks: %s
                """.formatted(
                snapshot.stageReady(),
                snapshot.planCompleted(),
                valueOrNone(snapshot.currentSubtaskTitle()),
                renderStringList(snapshot.incompleteSubtasks())
        ));
        if (!blank(snapshot.summary()).isBlank()) {
            builder.append("- summary: ").append(snapshot.summary().trim()).append('\n');
        }
        if (snapshot.contractGate() != null) {
            builder.append("""

                    ## Deterministic Contract Gate

                    - passed: %s
                    - scope: %s
                    - failureReason: %s
                    - details: %s
                    - patchTarget: %s
                    """.formatted(
                    snapshot.contractGate().passed(),
                    valueOrNone(snapshot.contractGate().scope()),
                    valueOrNone(snapshot.contractGate().failureReason()),
                    valueOrNone(snapshot.contractGate().details()),
                    valueOrNone(snapshot.contractGate().implementationPatchTarget())
            ));
        }
        if (hasContinuationDirective(snapshot)) {
            builder.append("""

                    ## Continuation

                    - mode: %s
                    - summary: %s
                    - changeRequest: %s
                    - evidence: %s
                    - actionItems: %s
                    - patchTarget: %s
                    - reasonCode: %s
                    - overrideChanges: %s
                    """.formatted(
                    continuationMode(snapshot).name(),
                    valueOrNone(snapshot.continuationSummary()),
                    valueOrNone(snapshot.continuationChangeRequest()),
                    valueOrNone(snapshot.continuationEvidence()),
                    valueOrNone(snapshot.continuationActionItems()),
                    patchTarget(snapshot.continuationPatchTarget()).name(),
                    reasonCode(snapshot.continuationReasonCode()).name(),
                    renderFileChanges(snapshot.continuationOverrideChanges())
            ));
        }
        builder.append("\n\n## Subtask Status\n\n");
        appendSubtaskSummary(builder, snapshot);
        appendDiagnostics(builder, snapshot);
        return builder.toString().trim();
    }

    public String renderImplementationReviewSummary(String stateJson) {
        return renderReviewSummary(stateJson);
    }

    private void appendSubtaskSummary(StringBuilder builder, ImplementationStateSnapshot snapshot) {
        List<ImplementationStateSnapshot.PlannedSubtaskState> subtasks = safeList(snapshot.subtasks());
        List<ImplementationStateSnapshot.SubtaskExecutionStateSnapshot> reports = safeList(snapshot.reports());
        if (subtasks.isEmpty() && reports.isEmpty()) {
            builder.append("- ").append(PlaceholderValues.machineNone());
            return;
        }
        int limit = Math.max(subtasks.size(), reports.size());
        for (int index = 0; index < limit; index++) {
            ImplementationStateSnapshot.PlannedSubtaskState subtask = index < subtasks.size() ? subtasks.get(index) : null;
            ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report = index < reports.size() ? reports.get(index) : null;
            List<ImplementationStateSnapshot.FileChangeState> effectiveChanges = effectiveChanges(subtask, report);
            builder.append("- title: ").append(valueOrNone(subtask == null ? "" : subtask.title()))
                    .append(" | completed: ").append(report != null && report.completed())
                    .append(" | deliveryMode: ").append(valueOrNone(report == null ? "" : report.deliveryMode()))
                    .append(" | changes: ").append(renderFileChanges(effectiveChanges))
                    .append('\n');
            ImplementationStateSnapshot.SubtaskAttemptState latestAttempt = latestAttempt(report);
            if (latestAttempt != null) {
                builder.append("  latestSelfCheck: ")
                        .append(valueOrNone(latestAttempt.selfCheckSummary()))
                        .append('\n');
                builder.append("  latestReview: ")
                        .append(valueOrNone(latestAttempt.reviewDecision()))
                        .append('/')
                        .append(valueOrNone(latestAttempt.reviewFixMode()))
                        .append(" | ")
                        .append(valueOrNone(latestAttempt.reviewSummary()))
                        .append('\n');
            }
        }
    }

    private void appendDiagnostics(StringBuilder builder, ImplementationStateSnapshot snapshot) {
        List<ImplementationStateSnapshot.DiagnosticState> diagnostics = collectDiagnostics(snapshot.reports());
        if (diagnostics.isEmpty()) {
            return;
        }
        builder.append("\n## Diagnostics\n\n");
        for (ImplementationStateSnapshot.DiagnosticState diagnostic : diagnostics) {
            if (diagnostic == null) {
                continue;
            }
            String relativePath = blank(diagnostic.relativePath()).isBlank()
                    ? "(tool-loop)"
                    : diagnostic.relativePath();
            builder.append("- ")
                    .append(relativePath)
                    .append(" | ")
                    .append(valueOrNone(diagnostic.status()))
                    .append(" | ")
                    .append(valueOrNone(diagnostic.source()))
                    .append(" | ")
                    .append(blank(diagnostic.failureCode()).isBlank()
                            ? ""
                            : diagnostic.failureCode() + " | ")
                    .append(valueOrNone(diagnostic.evidence()))
                    .append('\n');
        }
    }

    private List<ImplementationStateSnapshot.DiagnosticState> collectDiagnostics(
            List<ImplementationStateSnapshot.SubtaskExecutionStateSnapshot> reports
    ) {
        List<ImplementationStateSnapshot.DiagnosticState> diagnostics = new ArrayList<>();
        for (ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report : safeList(reports)) {
            if (report == null || report.toolSessionState() == null || report.toolSessionState().diagnostics() == null) {
                continue;
            }
            diagnostics.addAll(report.toolSessionState().diagnostics());
        }
        return List.copyOf(diagnostics);
    }

    private List<ImplementationStateSnapshot.FileChangeState> effectiveChanges(
            ImplementationStateSnapshot.PlannedSubtaskState subtask,
            ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report
    ) {
        if (report != null && report.effectiveChanges() != null && !report.effectiveChanges().isEmpty()) {
            return report.effectiveChanges();
        }
        return subtask == null ? List.of() : safeList(subtask.changes());
    }

    private ImplementationStateSnapshot.SubtaskAttemptState latestAttempt(
            ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report
    ) {
        List<ImplementationStateSnapshot.SubtaskAttemptState> attempts =
                report == null ? List.of() : safeList(report.attempts());
        return attempts.isEmpty() ? null : attempts.getLast();
    }

    private boolean hasContinuationDirective(ImplementationStateSnapshot snapshot) {
        return continuationMode(snapshot) == ImplementationContinuationMode.BLOCK_STAGE
                || patchTarget(snapshot.continuationPatchTarget()).concretePatch()
                || reasonCode(snapshot.continuationReasonCode()) != ReviewReasonCode.NONE
                || !blank(snapshot.continuationSummary()).isBlank()
                || !blank(snapshot.continuationChangeRequest()).isBlank()
                || !blank(snapshot.continuationEvidence()).isBlank()
                || !blank(snapshot.continuationActionItems()).isBlank()
                || !safeList(snapshot.continuationOverrideChanges()).isEmpty();
    }

    private ImplementationContinuationMode continuationMode(ImplementationStateSnapshot snapshot) {
        return parseRequiredEnum(
                ImplementationContinuationMode.class,
                snapshot == null ? "" : snapshot.continuationMode(),
                "continuationMode"
        );
    }

    private ImplementationPatchTarget patchTarget(String rawValue) {
        return parseRequiredEnum(
                ImplementationPatchTarget.class,
                rawValue,
                "continuationPatchTarget"
        );
    }

    private ReviewReasonCode reasonCode(String rawValue) {
        return parseRequiredEnum(
                ReviewReasonCode.class,
                rawValue,
                "continuationReasonCode"
        );
    }

    private ImplementationStageStatusPayload.ContractGatePayload toContractGatePayload(
            ImplementationStateSnapshot.ContractGateState contractGate
    ) {
        if (contractGate == null) {
            return null;
        }
        return new ImplementationStageStatusPayload.ContractGatePayload(
                contractGate.scope(),
                contractGate.passed(),
                blank(contractGate.failureReason()),
                blank(contractGate.details()),
                blank(contractGate.implementationPatchTarget()),
                toRuntimeContractPayload(contractGate.runtimeContract())
        );
    }

    private ImplementationStageStatusPayload.RuntimeContractPayload toRuntimeContractPayload(
            ImplementationStateSnapshot.RuntimeContractState runtimeContract
    ) {
        if (runtimeContract == null) {
            return null;
        }
        return new ImplementationStageStatusPayload.RuntimeContractPayload(
                blank(runtimeContract.htmlEntryPath()),
                blank(runtimeContract.runtimeOwnership()),
                safeList(runtimeContract.runtimePaths())
        );
    }

    private List<FileChangePayload> toFileChangePayloads(
            List<ImplementationStateSnapshot.FileChangeState> changes
    ) {
        return safeList(changes).stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .map(change -> new FileChangePayload(
                        change.path(),
                        change.action(),
                        change.reason(),
                        change.editScope(),
                        change.runtimeOwnership(),
                        change.hostHtmlPatchRequired()
                ))
                .toList();
    }

    private ImplementationStateSnapshot requireSnapshot(String stateJson) {
        return stateCodec.readRequired(stateJson);
    }

    private String renderFileChanges(List<ImplementationStateSnapshot.FileChangeState> changes) {
        List<String> rendered = safeList(changes).stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .map(change -> "%s:%s".formatted(valueOrNone(change.action()), change.path().replace('\\', '/')))
                .toList();
        return rendered.isEmpty() ? "[]" : rendered.toString();
    }

    private String renderStringList(List<String> values) {
        List<String> normalized = safeList(values).stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
        return normalized.isEmpty() ? "[]" : normalized.toString();
    }

    private String valueOrNone(String value) {
        return blank(value).isBlank() ? PlaceholderValues.machineNone() : value.trim();
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private <T extends Enum<T>> T parseRequiredEnum(Class<T> enumType, String rawValue, String fieldName) {
        String normalized = blank(rawValue);
        if (normalized.isBlank()) {
            throw new IllegalStateException(
                    "Invalid implementation_state auxiliary artifact: missing enum field '" + fieldName + "'."
            );
        }
        for (T constant : enumType.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(normalized)) {
                return constant;
            }
        }
        throw new IllegalStateException(
                "Invalid implementation_state auxiliary artifact: unknown " + fieldName + "='" + rawValue + "'."
        );
    }
}
