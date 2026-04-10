package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;

/**
 * 文件级 patch 请求构造器。
 *
 * <p>代码 patch、宿主嵌入 patch 和 whole-file 例外路径都共享同一份
 * file-scoped context，这里统一装配，避免工厂类继续维护三套重复逻辑。
 */
final class FileScopedPatchRequestBuilder {

    private final PatchRequestContextFactory patchRequestContextFactory;

    FileScopedPatchRequestBuilder(PatchRequestContextFactory patchRequestContextFactory) {
        this.patchRequestContextFactory = patchRequestContextFactory;
    }

    EmbeddedPatchRequest embedded(
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
            FilePatchProgressState patchProgressState
    ) {
        PatchRequestContext context = buildContext(projectPath, relativePath, subtask, taskPackage, contractView, fingerprint);
        return new EmbeddedPatchRequest(
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
                patchProgressState
        );
    }

    CodePatchRequest code(
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
            FilePatchProgressState patchProgressState
    ) {
        PatchRequestContext context = buildContext(projectPath, relativePath, subtask, taskPackage, contractView, fingerprint);
        return new CodePatchRequest(
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
                patchProgressState
        );
    }

    WholeFilePatchRequest wholeFile(
            Path projectPath,
            Path relativePath,
            String planSummary,
            Subtask subtask,
            TaskPackage taskPackage,
            String feedback,
            String reason,
            String existingContent,
            DeliveryMode deliveryMode,
            ContractView contractView,
            ProjectFingerprint fingerprint,
            ImplementationEventJournal eventJournal
    ) {
        PatchRequestContext context = buildContext(projectPath, relativePath, subtask, taskPackage, contractView, fingerprint);
        return new WholeFilePatchRequest(
                projectPath,
                relativePath,
                planSummary,
                context.taskPackageMarkdown(),
                context.targetedContext(),
                feedback,
                reason,
                existingContent,
                deliveryMode,
                eventJournal
        );
    }

    private PatchRequestContext buildContext(
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
