package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.List;

/**
 * 负责把任务包压缩到文件级视角。
 *
 * <p>这层只做任务包相关的确定性变换：
 * 1. 当上游没有显式 task package 时，为当前 subtask 组装最小 task package；
 * 2. 把 task package 收缩到单文件；
 * 3. 生成供 patch 请求使用的 compact markdown。
 */
final class ScopedTaskPackageSupport {

    private final TargetedFileContextRenderer targetedFileContextRenderer;
    private final TaskPackageMarkdownRenderer taskPackageMarkdownRenderer;

    ScopedTaskPackageSupport(
            TargetedFileContextRenderer targetedFileContextRenderer,
            TaskPackageMarkdownRenderer taskPackageMarkdownRenderer
    ) {
        this.targetedFileContextRenderer = targetedFileContextRenderer;
        this.taskPackageMarkdownRenderer = taskPackageMarkdownRenderer;
    }

    TaskPackage scopeTaskPackageToFile(
            Path projectPath,
            Path relativePath,
            Subtask subtask,
            TaskPackage taskPackage,
            ContractView contractView,
            ProjectFingerprint fingerprint
    ) {
        TaskPackage base = taskPackage == null
                ? new TaskPackage(
                subtask.title(),
                subtask.goal(),
                subtask.deliveryMode().name(),
                subtask.runnableMilestone(),
                subtask.changes().stream().map(FileChange::path).toList(),
                safeList(subtask.coverageRefs()),
                safeList(subtask.ownedCapabilities()),
                safeList(subtask.deferredCapabilities()),
                safeList(subtask.acceptanceCriteria()),
                List.of(),
                List.of(),
                targetedFileContextRenderer.render(projectPath, subtask.changes(), null, contractView, fingerprint),
                null
        )
                : taskPackage;
        return base.scopeToFile(
                relativePath.toString(),
                targetedFileContextRenderer.render(projectPath, subtask.changes(), relativePath, contractView, fingerprint)
        );
    }

    String renderCompactTaskPackage(
            Path projectPath,
            Subtask subtask,
            SharedContextBundle sharedContextBundle,
            ContractView contractView,
            ProjectFingerprint fingerprint,
            Path relativePath
    ) {
        return taskPackageMarkdownRenderer.renderCompact(
                projectPath,
                subtask,
                sharedContextBundle,
                contractView,
                fingerprint,
                relativePath,
                targetedFileContextRenderer::render
        );
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
