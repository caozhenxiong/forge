package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责从 implementation plan 派生 task package。
 *
 * <p>这层只关心“子任务对 worker 暴露什么上下文”，避免快照装配器同时承担
 * task package 投影和 runtime snapshot 组装两类职责。
 */
final class TaskPackageAssembler {

    private final TargetedFileContextRenderer targetedFileContextRenderer;

    TaskPackageAssembler(TargetedFileContextRenderer targetedFileContextRenderer) {
        this.targetedFileContextRenderer = targetedFileContextRenderer;
    }

    List<TaskPackage> buildTaskPackages(
            Path projectPath,
            ImplementationPlan plan,
            SharedContextBundle sharedContextBundle,
            ContractView contractView,
            ProjectFingerprint fingerprint
    ) {
        List<TaskPackage> packages = new ArrayList<>();
        for (Subtask subtask : plan.subtasks()) {
            packages.add(new TaskPackage(
                    subtask.title(),
                    subtask.goal(),
                    subtask.deliveryMode().name(),
                    subtask.runnableMilestone(),
                    subtask.changes().stream().map(FileChange::path).toList(),
                    safeList(subtask.coverageRefs()),
                    safeList(subtask.ownedCapabilities()),
                    safeList(subtask.deferredCapabilities()),
                    safeList(subtask.acceptanceCriteria()),
                    sharedContextBundle.mustFixFirst(),
                    sharedContextBundle.forbiddenDirections(),
                    renderTargetedContext(projectPath, subtask.changes(), contractView, fingerprint),
                    sharedContextBundle
            ));
        }
        return packages;
    }

    private String renderTargetedContext(
            Path projectPath,
            List<FileChange> changes,
            ContractView contractView,
            ProjectFingerprint fingerprint
    ) {
        if (targetedFileContextRenderer == null) {
            return "";
        }
        return targetedFileContextRenderer.render(projectPath, changes, null, contractView, fingerprint);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
