package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        List<FileChange> activeChanges = executionState == null
                ? (subtask == null || subtask.changes() == null ? List.of() : subtask.changes())
                : executionState.effectiveChanges(subtask == null ? List.of() : subtask.changes());
        FilePatchProgressState patchProgressState = executionState == null
                ? null
                : executionState.filePatchProgress(relativePath);
        boolean fileExists = Files.exists(projectPath.resolve(relativePath));
        String persistedContent = fileExists
                ? workspace.readFile(projectPath, relativePath)
                : "";
        String existingContent = executionState == null
                ? persistedContent
                : effectiveExistingContent(relativePath, executionState, patchProgressState, persistedContent);
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
                resolveScopedChange(relativePath, activeChanges),
                resolveRuntimeContract(projectPath, relativePath, activeChanges)
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
            FilePatchProgressState patchProgressState,
            String persistedContent
    ) {
        if (relativePath != null
                && ProjectPathSupport.isHtml(relativePath)
                && patchProgressState != null
                && isEmbeddedWorksetProgress(patchProgressState.strategyName())) {
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
            List<FileChange> activeChanges
    ) {
        if (relativePath == null || !ProjectPathSupport.isHtml(relativePath)) {
            return null;
        }
        FileChange scopedChange = resolveScopedChange(relativePath, activeChanges);
        if (scopedChange == null || scopedChange.runtimeOwnership() == null) {
            return null;
        }
        if (scopedChange.runtimeOwnership() == RuntimeOwnershipMode.INLINE_HOST) {
            return HtmlRuntimeOwnershipContract.inlineHost(relativePath.normalize());
        }
        RuntimeScriptGraphInspector runtimeScriptGraphInspector = new RuntimeScriptGraphInspector(workspace);
        Path htmlParent = relativePath.getParent() == null ? Path.of("") : relativePath.getParent().normalize();
        List<Path> declaredRuntimeScripts = (activeChanges == null ? List.<FileChange>of() : activeChanges).stream()
                .filter(change -> change != null
                        && change.action() != ChangeAction.DELETE
                        && change.path() != null
                        && !change.path().isBlank())
                .map(change -> Path.of(change.path()).normalize())
                .filter(ProjectPathSupport::isRuntimeScript)
                .filter(path -> isUnderHtmlEntryTree(path, htmlParent))
                .toList();
        List<Path> runtimeRoots = runtimeScriptGraphInspector.selectDeclaredRoots(projectPath, declaredRuntimeScripts);
        if (runtimeRoots.isEmpty()) {
            RuntimeScriptGraphInspector.RuntimeScriptGraph graph = runtimeScriptGraphInspector.inspectProject(projectPath, relativePath);
            runtimeRoots = runtimeScriptGraphInspector.selectRootScripts(graph.runtimeScripts(), graph);
        }
        return HtmlRuntimeOwnershipContract.externalCompanion(relativePath.normalize(), runtimeRoots);
    }

    private boolean isUnderHtmlEntryTree(Path candidate, Path htmlParent) {
        Path candidateParent = candidate.getParent() == null ? Path.of("") : candidate.getParent().normalize();
        return htmlParent.toString().isBlank() || candidateParent.equals(htmlParent) || candidateParent.startsWith(htmlParent);
    }

    private boolean isEmbeddedWorksetProgress(String strategyName) {
        return FileEditStrategyNames.INLINE_SCRIPT_WORKSET.equals(strategyName)
                || FileEditStrategyNames.INLINE_STYLE_WORKSET.equals(strategyName);
    }
}
