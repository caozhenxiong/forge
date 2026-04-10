package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.validation.ProjectFingerprint;
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

    ImplementationContinuationConstraintResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ImplementationContinuationConstraints resolve(String previousStateJson, ProjectFingerprint fingerprint) {
        if (previousStateJson == null || previousStateJson.isBlank()) {
            return ImplementationContinuationConstraints.empty();
        }
        try {
            ImplementationStateSnapshot snapshot =
                    objectMapper.readValue(previousStateJson, ImplementationStateSnapshot.class);
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
            if (report != null && report.completed() && subtask != null && subtask.changes() != null) {
                for (ImplementationStateSnapshot.FileChangeState change : subtask.changes()) {
                    if (change != null && change.path() != null && !change.path().isBlank()) {
                        existingPaths.add(change.path());
                    }
                }
            }
            if (report != null && report.filePatchProgressStates() != null) {
                for (ImplementationStateSnapshot.FilePatchProgressStateSnapshot progress : report.filePatchProgressStates()) {
                    if (progress != null && progress.relativePath() != null && !progress.relativePath().isBlank()) {
                        existingPaths.add(progress.relativePath());
                    }
                }
            }
        }
        Set<String> protectedHtmlEntryPaths = new LinkedHashSet<>();
        RuntimeOwnershipMode protectedRuntimeOwnership = null;
        String resolvedHtmlEntryPath = fingerprint == null ? "" : fingerprint.resolvedHtmlEntryPath();
        if (fingerprint != null && fingerprint.hasResolvedHtmlEntry()) {
            protectedHtmlEntryPaths.add(resolvedHtmlEntryPath);
            existingPaths.add(resolvedHtmlEntryPath);
        }
        if (!resolvedHtmlEntryPath.isBlank()) {
            for (int index = 0; index < limit; index++) {
                ImplementationStateSnapshot.PlannedSubtaskState subtask = plannedSubtasks.get(index);
                ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report = reports.get(index);
                if (report == null || !report.completed() || subtask == null || subtask.changes() == null) {
                    continue;
                }
                for (ImplementationStateSnapshot.FileChangeState change : subtask.changes()) {
                    if (change == null || change.path() == null || change.path().isBlank()) {
                        continue;
                    }
                    String normalizedPath = normalize(change.path());
                    if (!resolvedHtmlEntryPath.equals(normalizedPath)) {
                        continue;
                    }
                    protectedRuntimeOwnership = parseRuntimeOwnership(change.runtimeOwnership());
                }
            }
        }
        if (existingPaths.isEmpty() && protectedHtmlEntryPaths.isEmpty()) {
            return ImplementationContinuationConstraints.empty();
        }
        List<ImplementationContinuationConstraints.ProtectedHtmlEntryConstraint> protectedEntries = new java.util.ArrayList<>();
        for (String path : protectedHtmlEntryPaths) {
            protectedEntries.add(new ImplementationContinuationConstraints.ProtectedHtmlEntryConstraint(
                    path,
                    protectedRuntimeOwnership
            ));
        }
        return new ImplementationContinuationConstraints(
                List.copyOf(existingPaths),
                List.copyOf(protectedEntries)
        );
    }

    private String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return java.nio.file.Path.of(path).normalize().toString().replace('\\', '/');
    }

    private RuntimeOwnershipMode parseRuntimeOwnership(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RuntimeOwnershipMode.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
