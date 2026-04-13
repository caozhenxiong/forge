package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;

import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.TaskPackage;
/**
 * 负责为 patch 请求生成共享上下文文本。
 *
 * <p>宿主 HTML 与普通文件 patch 在 targeted context 取值上略有差异，
 * 这层把差异集中起来，避免 `FileProtocolRequestFactory` 继续重复同一套逻辑。
 */
final class FileProtocolRequestContextFactory {

    private final FileScopedContextSupport fileScopedContextSupport;

    FileProtocolRequestContextFactory(FileScopedContextSupport fileScopedContextSupport) {
        this.fileScopedContextSupport = fileScopedContextSupport;
    }

    FileProtocolRequestContext hostHtml(TaskPackage taskPackage) {
        return new FileProtocolRequestContext(
                taskPackage == null ? PlaceholderValues.machineNone() : taskPackage.toMarkdown(),
                taskPackage == null
                        ? PlaceholderValues.machineNoRelatedFiles()
                        : nullToEmpty(taskPackage.targetedContext())
        );
    }

    FileProtocolRequestContext filePatch(
            Path projectPath,
            Path relativePath,
            Subtask subtask,
            TaskPackage taskPackage,
            ContractView contractView,
            ProjectFingerprint fingerprint
    ) {
        return new FileProtocolRequestContext(
                taskPackage == null
                        ? fileScopedContextSupport.renderCompactTaskPackage(
                        projectPath,
                        subtask,
                        null,
                        contractView,
                        fingerprint,
                        relativePath
                )
                        : taskPackage.toMarkdown(),
                fileScopedContextSupport.renderTargetedContext(
                        projectPath,
                        subtask.changes(),
                        relativePath,
                        contractView,
                        fingerprint
                )
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
