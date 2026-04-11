package devflow.agent.executor;

import devflow.agent.util.EnumParsers;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * implementation snapshot 的唯一 runtime contract 恢复源。
 *
 * <p>这层要解决的是“completed snapshot 已经能证明当前 HTML 入口归谁持有主 runtime”，
 * 那么 resume policy、continuation constraint 和 state snapshot 都必须恢复出同一份 contract。
 * 不允许一处只认 architect 显式 contract，另一处又从文件变化里猜第二套答案。
 */
final class ImplementationRuntimeContractResolver {

    private final ImplementationSnapshotRestorer snapshotRestorer = new ImplementationSnapshotRestorer();

    HtmlRuntimeOwnershipContract resolve(ImplementationStateSnapshot snapshot) {
        return resolve(snapshot, null);
    }

    HtmlRuntimeOwnershipContract resolve(ImplementationStateSnapshot snapshot, String preferredHtmlEntryPath) {
        if (snapshot == null) {
            return null;
        }
        HtmlRuntimeOwnershipContract explicitContract =
                snapshotRestorer.restoreRuntimeContract(snapshot.architectRuntimeContract());
        Path resolvedHtmlEntryPath = resolveHtmlEntryPath(snapshot, preferredHtmlEntryPath, explicitContract);
        if (resolvedHtmlEntryPath == null) {
            return explicitContract != null && explicitContract.active() ? explicitContract : null;
        }
        RuntimeOwnershipResolution ownershipResolution = resolveOwnership(snapshot, resolvedHtmlEntryPath);
        if (matchesHtmlEntry(explicitContract, resolvedHtmlEntryPath)) {
            if (explicitContract.inlineHost()) {
                return explicitContract;
            }
            List<Path> explicitRuntimePaths = explicitContract.runtimePaths();
            if (!explicitRuntimePaths.isEmpty()) {
                return explicitContract;
            }
            List<Path> derivedRuntimePaths = resolveExternalRuntimePaths(
                    snapshot,
                    resolvedHtmlEntryPath,
                    ownershipResolution.subtaskIndex(),
                    explicitContract
            );
            return HtmlRuntimeOwnershipContract.externalCompanion(resolvedHtmlEntryPath, derivedRuntimePaths);
        }
        RuntimeOwnershipMode runtimeOwnership = ownershipResolution.runtimeOwnership();
        if (runtimeOwnership == null) {
            return null;
        }
        if (runtimeOwnership == RuntimeOwnershipMode.INLINE_HOST) {
            return HtmlRuntimeOwnershipContract.inlineHost(resolvedHtmlEntryPath);
        }
        List<Path> runtimePaths = resolveExternalRuntimePaths(
                snapshot,
                resolvedHtmlEntryPath,
                ownershipResolution.subtaskIndex(),
                explicitContract
        );
        return HtmlRuntimeOwnershipContract.externalCompanion(resolvedHtmlEntryPath, runtimePaths);
    }

    private Path resolveHtmlEntryPath(
            ImplementationStateSnapshot snapshot,
            String preferredHtmlEntryPath,
            HtmlRuntimeOwnershipContract explicitContract
    ) {
        Path preferred = normalizeHtml(preferredHtmlEntryPath);
        if (preferred != null) {
            return preferred;
        }
        if (explicitContract != null && explicitContract.active()) {
            return explicitContract.htmlEntryPath();
        }
        RuntimeOwnershipResolution ownershipResolution = resolveLatestHtmlOwnership(snapshot, null);
        return ownershipResolution.htmlEntryPath();
    }

    private RuntimeOwnershipResolution resolveOwnership(ImplementationStateSnapshot snapshot, Path htmlEntryPath) {
        RuntimeOwnershipResolution selected = resolveLatestHtmlOwnership(snapshot, htmlEntryPath);
        if (selected.runtimeOwnership() != null) {
            return selected;
        }
        return new RuntimeOwnershipResolution(htmlEntryPath, null, selected.subtaskIndex());
    }

    private RuntimeOwnershipResolution resolveLatestHtmlOwnership(
            ImplementationStateSnapshot snapshot,
            Path targetHtmlEntryPath
    ) {
        if (snapshot == null || snapshot.subtasks() == null || snapshot.reports() == null) {
            return new RuntimeOwnershipResolution(targetHtmlEntryPath, null, -1);
        }
        int limit = Math.min(snapshot.subtasks().size(), snapshot.reports().size());
        RuntimeOwnershipResolution selected = new RuntimeOwnershipResolution(targetHtmlEntryPath, null, -1);
        for (int index = 0; index < limit; index++) {
            ImplementationStateSnapshot.PlannedSubtaskState subtask = snapshot.subtasks().get(index);
            ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report = snapshot.reports().get(index);
            List<ImplementationStateSnapshot.FileChangeState> effectiveChanges = effectiveChanges(subtask, report);
            if (report == null || !report.completed() || effectiveChanges.isEmpty()) {
                continue;
            }
            for (ImplementationStateSnapshot.FileChangeState change : effectiveChanges) {
                if (change == null || change.path() == null || change.path().isBlank()) {
                    continue;
                }
                if (!ProjectPathSupport.isHtml(change.path())) {
                    continue;
                }
                if ("DELETE".equalsIgnoreCase(change.action())) {
                    continue;
                }
                Path changedHtmlPath = normalizeHtml(change.path());
                if (changedHtmlPath == null) {
                    continue;
                }
                if (targetHtmlEntryPath != null && !targetHtmlEntryPath.equals(changedHtmlPath)) {
                    continue;
                }
                selected = new RuntimeOwnershipResolution(
                        changedHtmlPath,
                        EnumParsers.parseIgnoreCase(RuntimeOwnershipMode.class, change.runtimeOwnership(), null),
                        index
                );
            }
        }
        return selected;
    }

    private List<Path> resolveExternalRuntimePaths(
            ImplementationStateSnapshot snapshot,
            Path htmlEntryPath,
            int ownerSubtaskIndex,
            HtmlRuntimeOwnershipContract explicitContract
    ) {
        if (matchesHtmlEntry(explicitContract, htmlEntryPath) && !explicitContract.runtimePaths().isEmpty()) {
            return explicitContract.runtimePaths();
        }
        List<Path> ownerSubtaskPaths = runtimeScriptChanges(snapshot, ownerSubtaskIndex);
        if (ownerSubtaskPaths.size() == 1) {
            return ownerSubtaskPaths;
        }
        List<Path> completedRuntimePaths = completedRuntimeScriptChanges(snapshot);
        if (completedRuntimePaths.size() == 1) {
            return completedRuntimePaths;
        }
        return List.of();
    }

    private List<Path> runtimeScriptChanges(ImplementationStateSnapshot snapshot, int subtaskIndex) {
        if (snapshot == null
                || snapshot.subtasks() == null
                || subtaskIndex < 0
                || subtaskIndex >= snapshot.subtasks().size()) {
            return List.of();
        }
        ImplementationStateSnapshot.PlannedSubtaskState subtask = snapshot.subtasks().get(subtaskIndex);
        ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report =
                snapshot.reports() == null || subtaskIndex >= snapshot.reports().size()
                        ? null
                        : snapshot.reports().get(subtaskIndex);
        List<ImplementationStateSnapshot.FileChangeState> effectiveChanges = effectiveChanges(subtask, report);
        if (effectiveChanges.isEmpty()) {
            return List.of();
        }
        Set<Path> paths = new LinkedHashSet<>();
        for (ImplementationStateSnapshot.FileChangeState change : effectiveChanges) {
            Path runtimePath = normalizeRuntimeScriptChange(change);
            if (runtimePath != null) {
                paths.add(runtimePath);
            }
        }
        return List.copyOf(paths);
    }

    private List<Path> completedRuntimeScriptChanges(ImplementationStateSnapshot snapshot) {
        if (snapshot == null || snapshot.subtasks() == null || snapshot.reports() == null) {
            return List.of();
        }
        int limit = Math.min(snapshot.subtasks().size(), snapshot.reports().size());
        Set<Path> paths = new LinkedHashSet<>();
        for (int index = 0; index < limit; index++) {
            ImplementationStateSnapshot.PlannedSubtaskState subtask = snapshot.subtasks().get(index);
            ImplementationStateSnapshot.SubtaskExecutionStateSnapshot report = snapshot.reports().get(index);
            List<ImplementationStateSnapshot.FileChangeState> effectiveChanges = effectiveChanges(subtask, report);
            if (report == null || !report.completed() || effectiveChanges.isEmpty()) {
                continue;
            }
            for (ImplementationStateSnapshot.FileChangeState change : effectiveChanges) {
                Path runtimePath = normalizeRuntimeScriptChange(change);
                if (runtimePath != null) {
                    paths.add(runtimePath);
                }
            }
        }
        return new ArrayList<>(paths);
    }

    private Path normalizeRuntimeScriptChange(ImplementationStateSnapshot.FileChangeState change) {
        if (change == null || change.path() == null || change.path().isBlank()) {
            return null;
        }
        if ("DELETE".equalsIgnoreCase(change.action()) || !ProjectPathSupport.isRuntimeScript(change.path())) {
            return null;
        }
        return Path.of(change.path()).normalize();
    }

    private Path normalizeHtml(String path) {
        if (path == null || path.isBlank() || !ProjectPathSupport.isHtml(path)) {
            return null;
        }
        return Path.of(path).normalize();
    }

    private boolean matchesHtmlEntry(HtmlRuntimeOwnershipContract contract, Path htmlEntryPath) {
        return contract != null
                && contract.active()
                && htmlEntryPath != null
                && htmlEntryPath.normalize().equals(contract.htmlEntryPath().normalize());
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

    private record RuntimeOwnershipResolution(
            Path htmlEntryPath,
            RuntimeOwnershipMode runtimeOwnership,
            int subtaskIndex
    ) {
    }
}
