package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.List;

import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.TaskPackage;
/**
 * 统一渲染紧凑版任务包 markdown。
 *
 * <p>文件编辑链只需要一份“当前子任务在当前文件/上下文下的紧凑任务包”。
 * 这层把组装逻辑从协调器中抽离，避免门面类继续手工拼接 `TaskPackage`。
 */
final class TaskPackageMarkdownRenderer {

    String renderCompact(
            Path projectPath,
            Subtask subtask,
            SharedContextBundle sharedContextBundle,
            ContractView contractView,
            ProjectFingerprint fingerprint,
            Path relativePath,
            TargetedContextRenderer targetedContextRenderer
    ) {
        TaskPackage taskPackage = new TaskPackage(
                subtask.title(),
                subtask.goal(),
                subtask.deliveryMode().name(),
                subtask.runnableMilestone(),
                subtask.changes().stream().map(FileChange::path).toList(),
                safeList(subtask.coverageRefs()),
                safeList(subtask.ownedCapabilities()),
                safeList(subtask.deferredCapabilities()),
                safeList(subtask.acceptanceCriteria()),
                sharedContextBundle == null ? List.of() : sharedContextBundle.mustFixFirst(),
                sharedContextBundle == null ? List.of() : sharedContextBundle.forbiddenDirections(),
                targetedContextRenderer.render(projectPath, subtask.changes(), relativePath, contractView, fingerprint),
                sharedContextBundle
        );
        return taskPackage.toMarkdown();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    @FunctionalInterface
    interface TargetedContextRenderer {
        String render(
                Path projectPath,
                List<FileChange> changes,
                Path relativePath,
                ContractView contractView,
                ProjectFingerprint fingerprint
        );
    }
}
