package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.editing.precise.FileStateLedger;
import devflow.agent.editing.precise.FileStateSnapshot;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionState;
import devflow.agent.executor.subtask.TaskPackage;
/**
 * 统一构造文件级 patch 路由请求。
 *
 * <p>这层把“读取现有内容、收敛 scoped task package、裁剪反馈、定位当前文件 change”
 * 从协调器里拿出来。协调器后续只负责：
 * 1. 创建请求；
 * 2. 选择文件执行门面；
 * 3. 做事务写盘。
 */
public final class FileEditRequestFactory {

    private final FileProjectWorkspace workspace;
    private final FileScopedContextSupport fileScopedContextSupport;
    private final FileStateLedger fileStateLedger;
    private final HtmlRuntimeContractResolver runtimeContractResolver;

    public FileEditRequestFactory(
            FileProjectWorkspace workspace,
            FileScopedContextSupport fileScopedContextSupport
    ) {
        this.workspace = workspace;
        this.fileScopedContextSupport = fileScopedContextSupport;
        this.fileStateLedger = new FileStateLedger();
        this.runtimeContractResolver = new HtmlRuntimeContractResolver(workspace);
    }

    FileEditRequest create(
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
        List<FileChange> activeChanges = executionState == null
                ? (subtask == null || subtask.changes() == null ? List.of() : subtask.changes())
                : executionState.effectiveChanges(subtask == null ? List.of() : subtask.changes());
        FileEditAttemptState editAttemptState = executionState == null
                ? null
                : executionState.fileEditAttemptState(relativePath);
        boolean fileExists = Files.exists(projectPath.resolve(relativePath));
        String persistedContent = fileExists
                ? workspace.readFile(projectPath, relativePath)
                : "";
        String existingContent = executionState == null
                ? persistedContent
                : effectiveExistingContent(relativePath, executionState, editAttemptState, persistedContent);
        FileStateSnapshot fileSnapshot = fileStateLedger.capture(relativePath, fileExists, existingContent);
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
                activeChanges.stream().map(change -> Path.of(change.path()).normalize()).toList(),
                existingContent,
                feedback
        );
        boolean substantiveFeedback = !StructuredArtifactBlocks.stripAllKnownBlocks(
                feedback == null ? "" : feedback
        ).isBlank();
        return new FileEditRequest(
                projectPath,
                relativePath,
                planSummary,
                subtask,
                fileScopedTaskPackage,
                fileScopedFeedback,
                substantiveFeedback,
                reason,
                existingContent,
                fileSnapshot,
                coderContextMarkdown,
                executionState,
                editAttemptState,
                contractView,
                fingerprint,
                eventJournal,
                resolveScopedChange(relativePath, activeChanges),
                resolveRuntimeContract(projectPath, relativePath, activeChanges, fingerprint, existingContent)
        );
    }

    /**
     * HTML 宿主在重判编辑骨架时，必须看到完整宿主内容，而不是内嵌脚本/样式的 workingContent。
     *
     * <p>`INLINE_SCRIPT_WORKSET / INLINE_STYLE_WORKSET` 的 progressState 里保存的是嵌入片段，
     * 只能给对应执行器做“是否继续沿用该骨架”的兼容性判断；如果把它直接冒充现有 HTML 内容，
     * 宿主级路由会误以为页面已经没有 script/style 锚点，导致 focused region / host patch 全部失效。
     */
    private String effectiveExistingContent(
            Path relativePath,
            SubtaskExecutionState executionState,
            FileEditAttemptState editAttemptState,
            String persistedContent
    ) {
        if (relativePath != null
                && ProjectPathSupport.isHtml(relativePath)
                && editAttemptState != null
                && isEmbeddedWorksetProgress(editAttemptState.strategyName())) {
            return persistedContent == null ? "" : persistedContent;
        }
        return executionState.effectiveExistingContent(relativePath, persistedContent);
    }

    private FileChange resolveScopedChange(Path relativePath, List<FileChange> activeChanges) {
        if (relativePath == null || activeChanges == null || activeChanges.isEmpty()) {
            return null;
        }
        Path normalized = relativePath.normalize();
        return activeChanges.stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .filter(change -> normalized.equals(Path.of(change.path()).normalize()))
                .findFirst()
                .orElse(null);
    }

    private HtmlRuntimeOwnershipContract resolveRuntimeContract(
            Path projectPath,
            Path relativePath,
            List<FileChange> activeChanges,
            ProjectFingerprint fingerprint,
            String existingContent
    ) {
        return runtimeContractResolver.resolveCanonicalContract(
                projectPath,
                relativePath,
                resolvedHostEntryPath(fingerprint),
                null,
                activeChanges,
                existingContent,
                List.of()
        );
    }

    private Path resolvedHostEntryPath(ProjectFingerprint fingerprint) {
        if (fingerprint == null || !fingerprint.hasResolvedHtmlEntry()) {
            return null;
        }
        return Path.of(fingerprint.resolvedHtmlEntryPath()).normalize();
    }

    private boolean isEmbeddedWorksetProgress(String strategyName) {
        return FileEditStrategyNames.INLINE_SCRIPT_WORKSET.equals(strategyName)
                || FileEditStrategyNames.INLINE_STYLE_WORKSET.equals(strategyName);
    }
}
