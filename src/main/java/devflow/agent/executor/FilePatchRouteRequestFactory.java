package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 统一构造文件级 patch 路由请求。
 *
 * <p>这层把“读取现有内容、收敛 scoped task package、裁剪反馈、定位当前文件 change”
 * 从协调器里拿出来。协调器后续只负责：
 * 1. 创建请求；
 * 2. 选择文件执行门面；
 * 3. 做事务写盘。
 */
final class FilePatchRouteRequestFactory {

    private final FileProjectWorkspace workspace;
    private final FileScopedContextSupport fileScopedContextSupport;

    FilePatchRouteRequestFactory(
            FileProjectWorkspace workspace,
            FileScopedContextSupport fileScopedContextSupport
    ) {
        this.workspace = workspace;
        this.fileScopedContextSupport = fileScopedContextSupport;
    }

    FilePatchRouteRequest create(
            Path projectPath,
            Path relativePath,
            String planSummary,
            Subtask subtask,
            TaskPackage taskPackage,
            String feedback,
            String reason,
            SubtaskExecutionState executionState,
            ContractView contractView,
            ProjectFingerprint fingerprint,
            String coderContextMarkdown,
            ImplementationEventJournal eventJournal
    ) {
        FilePatchProgressState patchProgressState = executionState == null
                ? null
                : executionState.filePatchProgress(relativePath);
        boolean fileExists = Files.exists(projectPath.resolve(relativePath));
        String persistedContent = fileExists
                ? workspace.readFile(projectPath, relativePath)
                : "";
        String existingContent = executionState == null
                ? persistedContent
                : executionState.effectiveExistingContent(relativePath, persistedContent);
        TaskPackage fileScopedTaskPackage = fileScopedContextSupport.scopeTaskPackageToFile(
                projectPath,
                relativePath,
                subtask,
                taskPackage,
                contractView,
                fingerprint
        );
        String fileScopedFeedback = fileScopedContextSupport.scopeFeedbackToFile(
                projectPath,
                relativePath,
                subtask == null || subtask.changes() == null
                        ? java.util.List.of()
                        : subtask.changes().stream().map(change -> Path.of(change.path()).normalize()).toList(),
                existingContent,
                feedback
        );
        boolean substantiveFeedback = !StructuredArtifactBlocks.stripAllKnownBlocks(
                feedback == null ? "" : feedback
        ).isBlank();
        return new FilePatchRouteRequest(
                projectPath,
                relativePath,
                planSummary,
                subtask,
                fileScopedTaskPackage,
                fileScopedFeedback,
                substantiveFeedback,
                reason,
                existingContent,
                coderContextMarkdown,
                executionState,
                patchProgressState,
                contractView,
                fingerprint,
                eventJournal,
                resolveScopedChange(subtask, relativePath)
        );
    }

    private FileChange resolveScopedChange(Subtask subtask, Path relativePath) {
        if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty() || relativePath == null) {
            return null;
        }
        Path normalized = relativePath.normalize();
        return subtask.changes().stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .filter(change -> normalized.equals(Path.of(change.path()).normalize()))
                .findFirst()
                .orElse(null);
    }
}
