package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractView;
import devflow.agent.editing.HtmlDocumentAssembler;
import devflow.agent.editing.CodePreciseEditor;
import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.util.ProjectPathSupport;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.List;

/**
 * 负责单文件级别的读取、局部生成、校验和事务写盘。
 * <p>
 * 这个协调器只处理“如何改一个文件”：
 * 1. 选择精确编辑、结构化 HTML 或整文件生成路径；
 * 2. 复用现有编辑器与 GenerationEngine 完成生成和恢复；
 * 3. 在本地做结构校验，再通过事务方式提交。
 * <p>
 * 它不负责：
 * 1. 子任务规划；
 * 2. 阶段完成判定；
 * 3. review / supervisor 语义决策。
 */
public class FileEditCoordinator {

    private final FileProjectWorkspace workspace;
    private final WholeFilePatchExecutor wholeFilePatchExecutor;
    private final HtmlFilePatchExecutor htmlFilePatchExecutor;
    private final CodeFilePatchExecutor codeFilePatchExecutor;
    private final FileScopedContextSupport fileScopedContextSupport;
    private final PatchRequestFactory patchRequestFactory;
    private final FilePatchRouteRequestFactory filePatchRouteRequestFactory;
    private final GeneratedFileCommitSupport generatedFileCommitSupport;

    public FileEditCoordinator(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TreeSitterSupport treeSitterSupport,
            HtmlPreciseEditor htmlPreciseEditor,
            CodePreciseEditor codePreciseEditor,
            HtmlDocumentAssembler htmlDocumentAssembler,
            GenerationEngine generationEngine,
            RuntimeWorkingSetResolver runtimeWorkingSetResolver,
            int maxFileGenerationAttempts
    ) {
        this.workspace = workspace;
        FileEditRuntimeComponents runtime = new FileEditRuntimeFactory().create(
                llmProvider,
                workspace,
                objectMapper,
                treeSitterSupport,
                htmlPreciseEditor,
                codePreciseEditor,
                htmlDocumentAssembler,
                generationEngine,
                runtimeWorkingSetResolver,
                maxFileGenerationAttempts
        );
        this.wholeFilePatchExecutor = runtime.wholeFilePatchExecutor();
        this.htmlFilePatchExecutor = runtime.htmlFilePatchExecutor();
        this.codeFilePatchExecutor = runtime.codeFilePatchExecutor();
        this.fileScopedContextSupport = runtime.fileScopedContextSupport();
        this.patchRequestFactory = runtime.patchRequestFactory();
        this.filePatchRouteRequestFactory = runtime.filePatchRouteRequestFactory();
        this.generatedFileCommitSupport = new GeneratedFileCommitSupport(workspace, runtime.generatedContentGate());
    }

    /**
     * 执行单个文件变更。
     * 该方法是子任务执行器和文件级生成逻辑的唯一接缝，避免 ImplementationExecutor 继续直接操纵大段文件生成代码。
     */
    SubtaskExecutionState applyChange(
            Path projectPath,
            String planSummary,
            Subtask subtask,
            TaskPackage taskPackage,
            String feedback,
            FileChange change,
            SubtaskExecutionState executionState,
            ContractView contractView,
            ProjectFingerprint fingerprint,
            String coderContextMarkdown,
            ImplementationEventJournal eventJournal
    ) {
        FileChange effectiveChange = executionState == null ? change : executionState.effectiveChange(change);
        Path relativePath = Path.of(effectiveChange.path()).normalize();
        appendImplementationEvent(
                eventJournal,
                ImplementationEventMessages.fileApplyStart(relativePath, effectiveChange.action(), executionState.deliveryMode())
        );
        try {
            if (effectiveChange.action() == ChangeAction.WRITE) {
                generatedFileCommitSupport.commitGeneratedOutput(
                        projectPath,
                        subtask,
                        effectiveChange,
                        generateFileOutput(
                                projectPath,
                                relativePath,
                                planSummary,
                                subtask,
                                taskPackage,
                                feedback,
                                effectiveChange.reason(),
                                executionState,
                                contractView,
                                fingerprint,
                                coderContextMarkdown,
                                eventJournal
                        )
                );
                executionState.clearPatchProgress(relativePath);
            } else {
                workspace.deleteFile(projectPath, relativePath);
                executionState.clearPatchProgress(relativePath);
            }
        } catch (GenerationFailureException exception) {
            if (exception.patchProgressState() != null) {
                executionState.recordPatchProgress(exception.patchProgressState());
            }
            throw exception;
        }
        appendImplementationEvent(
                eventJournal,
                ImplementationEventMessages.fileApplyFinished(relativePath, effectiveChange.action())
        );
        return executionState;
    }

    String renderTargetedContext(
            Path projectPath,
            List<FileChange> changes,
            Path currentPath,
            ContractView contractView,
            ProjectFingerprint fingerprint
    ) {
        return fileScopedContextSupport.renderTargetedContext(projectPath, changes, currentPath, contractView, fingerprint);
    }

    private GeneratedFileOutput generateFileOutput(
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
        FilePatchRouteRequest routeRequest = filePatchRouteRequestFactory.create(
                projectPath,
                relativePath,
                planSummary,
                subtask,
                taskPackage,
                feedback,
                reason,
                executionState,
                contractView,
                fingerprint,
                coderContextMarkdown,
                eventJournal
        );
        if (ProjectPathSupport.isHtml(relativePath)) {
            return htmlFilePatchExecutor.generate(routeRequest);
        }
        if (ProjectPathSupport.isPreciseCode(relativePath)) {
            return codeFilePatchExecutor.generate(routeRequest);
        }
        return GeneratedFileOutput.primaryOnly(wholeFilePatchExecutor.generate(
                patchRequestFactory.wholeFile(routeRequest)
        ));
    }

    private void appendImplementationEvent(ImplementationEventJournal eventJournal, String message) {
        if (eventJournal != null) {
            eventJournal.append(message);
        }
    }
}
