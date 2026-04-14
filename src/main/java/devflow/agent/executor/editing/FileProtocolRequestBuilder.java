package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.editing.precise.FileStateSnapshot;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.TaskPackage;
/**
 * 文件级 patch 请求构造器。
 *
 * <p>代码 patch、宿主嵌入 patch 和 whole-file 例外路径都共享同一份
 * file-scoped context，这里统一装配，避免工厂类继续维护三套重复逻辑。
 */
public final class FileProtocolRequestBuilder {

    private final FileProtocolRequestContextFactory patchRequestContextFactory;

    public FileProtocolRequestBuilder(FileProtocolRequestContextFactory patchRequestContextFactory) {
        this.patchRequestContextFactory = patchRequestContextFactory;
    }

    EmbeddedTargetedRewriteRequest embedded(
            Path projectPath,
            Path relativePath,
            String planSummary,
            Subtask subtask,
            TaskPackage taskPackage,
            String feedback,
            String reason,
            String existingContent,
            String coderContextMarkdown,
            DeliveryMode deliveryMode,
            ContractView contractView,
            ProjectFingerprint fingerprint,
            ImplementationEventJournal eventJournal,
            FileEditAttemptState editAttemptState,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        FileProtocolRequestContext context = buildContext(projectPath, relativePath, subtask, taskPackage, contractView, fingerprint);
        return new EmbeddedTargetedRewriteRequest(
                projectPath,
                relativePath,
                planSummary,
                context.taskPackageMarkdown(),
                context.targetedContext(),
                feedback,
                reason,
                existingContent,
                coderContextMarkdown,
                deliveryMode,
                eventJournal,
                editAttemptState,
                runtimeContract
        );
    }

    CodeTargetedRewriteRequest code(
            Path projectPath,
            Path relativePath,
            String planSummary,
            Subtask subtask,
            TaskPackage taskPackage,
            String feedback,
            boolean substantiveFeedback,
            String reason,
            String existingContent,
            String coderContextMarkdown,
            DeliveryMode deliveryMode,
            ContractView contractView,
            ProjectFingerprint fingerprint,
            ImplementationEventJournal eventJournal,
            FileEditAttemptState editAttemptState
    ) {
        FileProtocolRequestContext context = buildContext(projectPath, relativePath, subtask, taskPackage, contractView, fingerprint);
        return new CodeTargetedRewriteRequest(
                projectPath,
                relativePath,
                planSummary,
                context.taskPackageMarkdown(),
                context.targetedContext(),
                feedback,
                substantiveFeedback,
                reason,
                existingContent,
                coderContextMarkdown,
                deliveryMode,
                eventJournal,
                editAttemptState
        );
    }

    FullRewriteRequest wholeFile(
            Path projectPath,
            Path relativePath,
            String planSummary,
            Subtask subtask,
            TaskPackage taskPackage,
            String feedback,
            String reason,
            String existingContent,
            FileStateSnapshot fileSnapshot,
            DeliveryMode deliveryMode,
            ContractView contractView,
            ProjectFingerprint fingerprint,
            ImplementationEventJournal eventJournal,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        FileProtocolRequestContext context = buildContext(projectPath, relativePath, subtask, taskPackage, contractView, fingerprint);
        return new FullRewriteRequest(
                projectPath,
                relativePath,
                planSummary,
                context.taskPackageMarkdown(),
                context.targetedContext(),
                feedback,
                reason,
                existingContent,
                fileSnapshot,
                deliveryMode,
                eventJournal,
                runtimeContract
        );
    }

    private FileProtocolRequestContext buildContext(
            Path projectPath,
            Path relativePath,
            Subtask subtask,
            TaskPackage taskPackage,
            ContractView contractView,
            ProjectFingerprint fingerprint
    ) {
        return patchRequestContextFactory.filePatch(
                projectPath,
                relativePath,
                subtask,
                taskPackage,
                contractView,
                fingerprint
        );
    }
}
