package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.List;

/**
 * 统一处理文件级上下文投影、反馈裁剪和任务包收缩。
 *
 * <p>这部分逻辑本质上是确定性支持层：
 * 1. 计算当前文件的 targeted context；
 * 2. 把共享任务包收缩到单文件视角；
 * 3. 过滤只属于当前文件/当前符号的重试反馈。
 *
 * <p>把它从旧文件级编辑链拆出来后，上层编排只保留路由和执行职责。
 */
final class FileScopedContextSupport {

    private final TargetedFileContextRenderer targetedFileContextRenderer;
    private final FileFeedbackScopeSupport fileFeedbackScopeSupport;
    private final ScopedTaskPackageSupport scopedTaskPackageSupport;

    FileScopedContextSupport(
            FileProjectWorkspace workspace,
            RuntimeWorkingSetResolver runtimeWorkingSetResolver,
            PatchContextBuilder patchContextBuilder,
            TaskPackageMarkdownRenderer taskPackageMarkdownRenderer
    ) {
        this.targetedFileContextRenderer = new TargetedFileContextRenderer(workspace, runtimeWorkingSetResolver);
        this.fileFeedbackScopeSupport = new FileFeedbackScopeSupport(workspace, patchContextBuilder);
        this.scopedTaskPackageSupport = new ScopedTaskPackageSupport(
                this.targetedFileContextRenderer,
                taskPackageMarkdownRenderer
        );
    }

    String renderTargetedContext(
            Path projectPath,
            List<FileChange> changes,
            Path currentPath,
            ContractView contractView,
            ProjectFingerprint fingerprint
    ) {
        return targetedFileContextRenderer.render(projectPath, changes, currentPath, contractView, fingerprint);
    }

    TaskPackage scopeTaskPackageToFile(
            Path projectPath,
            Path relativePath,
            Subtask subtask,
            TaskPackage taskPackage,
            ContractView contractView,
            ProjectFingerprint fingerprint
    ) {
        return scopedTaskPackageSupport.scopeTaskPackageToFile(
                projectPath,
                relativePath,
                subtask,
                taskPackage,
                contractView,
                fingerprint
        );
    }

    String scopeFeedbackToFile(
            Path projectPath,
            Path relativePath,
            List<Path> changePaths,
            String existingContent,
            String feedback
    ) {
        return fileFeedbackScopeSupport.scope(projectPath, relativePath, changePaths, existingContent, feedback);
    }

    String renderCompactTaskPackage(
            Path projectPath,
            Subtask subtask,
            SharedContextBundle sharedContextBundle,
            ContractView contractView,
            ProjectFingerprint fingerprint,
            Path relativePath
    ) {
        return scopedTaskPackageSupport.renderCompactTaskPackage(
                projectPath,
                subtask,
                sharedContextBundle,
                contractView,
                fingerprint,
                relativePath
        );
    }
}
