package devflow.agent.executor.implementation;

import devflow.agent.executor.FileChange;
import devflow.agent.executor.runtime.HtmlRuntimeOwnershipContract;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 单一 owner 规则的唯一实现。
 *
 * <p>completed-plan PATCH 能否自动续跑，只允许由这层判断：
 * PATCH_EXISTING_IMPLEMENTATION 必须完整落在单个 completed subtask 的 declared owner 内；
 * PATCH_RUNTIME_WIRING 在 runtime roots 非空时必须命中确定的 runtime-root owner；
 * 只有 runtime roots 为空时，才允许退回唯一的 html owner。
 */
public final class CompletedPlanPatchOwnerResolver {

    public boolean hasSingleCompletedOwner(
            List<SubtaskExecutionReport> reports,
            List<FileChange> overrideChanges
    ) {
        return resolveUniqueOwnerIndex(reports, targetPaths(overrideChanges)) >= 0;
    }

    public int requireSingleCompletedOwnerIndex(
            List<SubtaskExecutionReport> reports,
            List<FileChange> overrideChanges
    ) {
        int ownerIndex = resolveUniqueOwnerIndex(reports, targetPaths(overrideChanges));
        if (ownerIndex >= 0) {
            return ownerIndex;
        }
        throw new IllegalStateException(
                "Completed implementation PATCH requires overrideChanges owned by a single completed subtask."
        );
    }

    public boolean hasResolvableRuntimeWiringOwner(
            List<SubtaskExecutionReport> reports,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        return resolveRuntimeWiringOwnerIndex(reports, runtimeContract) >= 0;
    }

    public int requireResolvableRuntimeWiringOwnerIndex(
            List<SubtaskExecutionReport> reports,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        int ownerIndex = resolveRuntimeWiringOwnerIndex(reports, runtimeContract);
        if (ownerIndex >= 0) {
            return ownerIndex;
        }
        throw new IllegalStateException(
                "Completed implementation PATCH could not find an owning subtask for the target paths."
        );
    }

    private int resolveRuntimeWiringOwnerIndex(
            List<SubtaskExecutionReport> reports,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        if (runtimeContract == null || !runtimeContract.active()) {
            return -1;
        }
        if (runtimeContract.externalCompanion() && !runtimeContract.hasResolvedWiringRepairScope()) {
            return -1;
        }
        Set<Path> runtimeRootPaths = runtimeRootPaths(runtimeContract);
        int runtimeRootOwnerIndex = findLatestOwnerIndex(reports, runtimeRootPaths);
        if (runtimeRootOwnerIndex >= 0) {
            return runtimeRootOwnerIndex;
        }
        if (!runtimeRootPaths.isEmpty()) {
            return -1;
        }
        Path htmlEntryPath = runtimeContract.htmlEntryPath();
        if (htmlEntryPath == null) {
            return -1;
        }
        return resolveUniqueOwnerIndex(reports, Set.of(htmlEntryPath.normalize()));
    }

    private int findLatestOwnerIndex(
            List<SubtaskExecutionReport> reports,
            Set<Path> ownerPaths
    ) {
        if (reports == null || reports.isEmpty() || ownerPaths.isEmpty()) {
            return -1;
        }
        for (int index = reports.size() - 1; index >= 0; index--) {
            SubtaskExecutionReport report = reports.get(index);
            if (report == null || !report.completed()) {
                continue;
            }
            if (declaredOwnerPaths(report).containsAll(ownerPaths)) {
                return index;
            }
        }
        return -1;
    }

    private int resolveUniqueOwnerIndex(
            List<SubtaskExecutionReport> reports,
            Set<Path> targetPaths
    ) {
        if (reports == null || reports.isEmpty() || targetPaths.isEmpty()) {
            return -1;
        }
        Integer matchedIndex = null;
        for (int index = 0; index < reports.size(); index++) {
            SubtaskExecutionReport report = reports.get(index);
            if (report == null || !report.completed()) {
                continue;
            }
            if (!declaredOwnerPaths(report).containsAll(targetPaths)) {
                continue;
            }
            if (matchedIndex != null) {
                return -1;
            }
            matchedIndex = index;
        }
        return matchedIndex == null ? -1 : matchedIndex;
    }

    private Set<Path> runtimeRootPaths(HtmlRuntimeOwnershipContract runtimeContract) {
        if (runtimeContract == null || !runtimeContract.externalCompanion()) {
            return Set.of();
        }
        LinkedHashSet<Path> runtimeRootPaths = new LinkedHashSet<>();
        for (Path runtimePath : runtimeContract.runtimePaths()) {
            if (runtimePath == null) {
                continue;
            }
            runtimeRootPaths.add(runtimePath.normalize());
        }
        return runtimeRootPaths.isEmpty() ? Set.of() : Set.copyOf(runtimeRootPaths);
    }

    private Set<Path> declaredOwnerPaths(SubtaskExecutionReport report) {
        if (report == null || report.subtask() == null || report.subtask().changes() == null) {
            return Set.of();
        }
        return targetPaths(report.subtask().changes());
    }

    private Set<Path> targetPaths(List<FileChange> changes) {
        LinkedHashSet<Path> targetPaths = new LinkedHashSet<>();
        if (changes != null) {
            for (FileChange change : changes) {
                if (change == null || change.path() == null || change.path().isBlank()) {
                    continue;
                }
                targetPaths.add(Path.of(change.path()).normalize());
            }
        }
        return targetPaths.isEmpty() ? Set.of() : Set.copyOf(targetPaths);
    }
}
