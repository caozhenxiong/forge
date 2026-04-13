package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从上一轮 implementation snapshot 中恢复 continuation/replanning 约束。
 *
 * <p>这层不负责恢复可继续执行的 subtasks，只负责提取“已经存在且不可回退”的文件事实。
 */
final class ImplementationContinuationConstraintResolver {

    private final ObjectMapper objectMapper;
    private final ImplementationSnapshotRestorer snapshotRestorer;

    ImplementationContinuationConstraintResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.snapshotRestorer = new ImplementationSnapshotRestorer();
    }

    ImplementationContinuationConstraints resolve(String previousStateJson, ProjectFingerprint fingerprint) {
        if (previousStateJson == null || previousStateJson.isBlank()) {
            return ImplementationContinuationConstraints.empty();
        }
        try {
            ImplementationStateSnapshot snapshot = objectMapper.readValue(previousStateJson, ImplementationStateSnapshot.class);
            return derive(snapshot, fingerprint);
        } catch (Exception ignored) {
            return ImplementationContinuationConstraints.empty();
        }
    }

    private ImplementationContinuationConstraints derive(
            ImplementationStateSnapshot snapshot,
            ProjectFingerprint fingerprint
    ) {
        if (snapshot == null) {
            return ImplementationContinuationConstraints.empty();
        }
        Set<String> existingPaths = new LinkedHashSet<>();
        List<ImplementationStateSnapshot.PlannedSubtaskState> plannedSubtasks =
                snapshot.subtasks() == null ? List.of() : snapshot.subtasks();
        List<ImplementationStateSnapshot.SubtaskExecutionStateSnapshot> reports =
                snapshot.reports() == null ? List.of() : snapshot.reports();
        int limit = Math.min(plannedSubtasks.size(), reports.size());
        for (int index = 0; index < limit; index++) {
            ImplementationStateSnapshot.PlannedSubtaskState subtask = plannedSubtasks.get(index);
            ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report = reports.get(index);
            List<ImplementationStateSnapshot.FileChangeState> effectiveChanges = effectiveChanges(subtask, report);
            if (report != null && report.completed() && !effectiveChanges.isEmpty()) {
                for (ImplementationStateSnapshot.FileChangeState change : effectiveChanges) {
                    if (change != null && change.path() != null && !change.path().isBlank()) {
                        existingPaths.add(normalize(change.path()));
                    }
                }
            }
            if (report != null && report.fileEditAttemptStates() != null) {
                for (ImplementationStateSnapshot.FileEditAttemptStateSnapshot progress : report.fileEditAttemptStates()) {
                    if (progress != null && progress.relativePath() != null && !progress.relativePath().isBlank()) {
                        existingPaths.add(normalize(progress.relativePath()));
                    }
                }
            }
        }
        String resolvedHtmlEntryPath = fingerprint == null ? "" : normalize(fingerprint.resolvedHtmlEntryPath());
        if (!resolvedHtmlEntryPath.isBlank()) {
            existingPaths.add(resolvedHtmlEntryPath);
        }
        ArchitectIntegrationCheckResult contractGateResult = snapshotRestorer.restoreContractGate(snapshot.contractGate());
        HtmlRuntimeOwnershipContract protectedRuntimeContract = contractGateResult == null
                ? null
                : contractGateResult.runtimeContract();
        if (existingPaths.isEmpty() && protectedRuntimeContract == null) {
            return ImplementationContinuationConstraints.empty();
        }
        List<ImplementationContinuationConstraints.ProtectedHtmlEntryConstraint> protectedEntries =
                resolvedHtmlEntryPath.isBlank()
                        ? List.of()
                        : List.of(new ImplementationContinuationConstraints.ProtectedHtmlEntryConstraint(
                                resolvedHtmlEntryPath,
                                protectedRuntimeContract
                        ));
        return new ImplementationContinuationConstraints(List.copyOf(existingPaths), protectedEntries);
    }

    private List<ImplementationStateSnapshot.FileChangeState> effectiveChanges(
            ImplementationStateSnapshot.PlannedSubtaskState subtask,
            ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report
    ) {
        if (report != null && report.effectiveChanges() != null && !report.effectiveChanges().isEmpty()) {
            return report.effectiveChanges();
        }
        return subtask == null || subtask.changes() == null ? List.of() : subtask.changes();
    }

    private String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return Path.of(path).normalize().toString().replace('\\', '/');
    }

}
