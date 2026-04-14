package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.precise.HtmlPreciseEditor;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 负责文件级编辑策略选择。
 * 这个类只回答“当前文件更适合走哪条编辑路径”，不负责真正生成、组装、校验和写盘。
 *
 * <p>代码文件相关判断现在统一通过 `LanguageEditAdapter` 完成，
 * 避免策略层再次直接依赖某一种语言编辑器实现。
 */
public class FileEditStrategyResolver {

    private final HtmlPreciseEditor htmlPreciseEditor;
    private final LanguageEditAdapter codeEditAdapter;

    public FileEditStrategyResolver(HtmlPreciseEditor htmlPreciseEditor, LanguageEditAdapter codeEditAdapter) {
        this.htmlPreciseEditor = htmlPreciseEditor;
        this.codeEditAdapter = codeEditAdapter;
    }

    public boolean shouldUseStructuredHtmlDocumentGeneration(Path projectPath, Path relativePath, String existingContent) {
        return ProjectPathSupport.isHtml(relativePath)
                && PlaceholderValues.isNewFileLiteral(existingContent)
                && !Files.exists(projectPath.resolve(relativePath));
    }

    boolean shouldUsePreciseHtmlEditing(
            Path projectPath,
            Path relativePath,
            DeliveryMode deliveryMode,
            boolean preferPreciseEditing,
            String existingContent
    ) {
        if (!ProjectPathSupport.isHtml(relativePath)) {
            return false;
        }
        if (!Files.exists(projectPath.resolve(relativePath))) {
            return false;
        }
        if (deliveryMode == DeliveryMode.SKELETON || deliveryMode == DeliveryMode.REWORK) {
            return false;
        }
        return htmlPreciseEditor.supportsPreciseEditing(existingContent);
    }

    boolean shouldUsePreciseCodeEditing(
            Path projectPath,
            Path relativePath,
            DeliveryMode deliveryMode,
            boolean preferPreciseEditing,
            String existingContent
    ) {
        boolean fileExists = Files.exists(projectPath.resolve(relativePath));
        if (!fileExists) {
            return codeEditAdapter.supports(relativePath)
                    && deliveryMode != DeliveryMode.REWORK
                    && codeEditAdapter.supportsAppendOnlyEditing(relativePath, existingContent);
        }
        if (!supportsLocalCodeEditing(relativePath, existingContent)) {
            return false;
        }
        if (mustUseLocalCodeEditing(projectPath, relativePath, deliveryMode)) {
            return true;
        }
        if (!preferPreciseEditing) {
            return false;
        }
        return deliveryMode != DeliveryMode.SKELETON && deliveryMode != DeliveryMode.REWORK;
    }

    /**
     * 已有代码文件在 PATCH / INCREMENTAL 下默认必须走局部编辑。
     * 这是对旧的 whole-file 主路径做硬切换，避免代码文件继续整文件往返。
     */
    public boolean mustUseLocalCodeEditing(Path projectPath, Path relativePath, DeliveryMode deliveryMode) {
        return codeEditAdapter.supports(relativePath)
                && deliveryMode != DeliveryMode.REWORK
                && (Files.exists(projectPath.resolve(relativePath))
                || codeEditAdapter.supportsAppendOnlyEditing(relativePath, ""))
                && deliveryMode != DeliveryMode.SKELETON
                ;
    }

    /**
     * 只有新建文件或显式 REWORK 才允许代码文件走整文件重写。
     */
    public boolean canUseWholeFileRewriteForCode(Path projectPath, Path relativePath, DeliveryMode deliveryMode) {
        if (!ProjectPathSupport.isPreciseCode(relativePath)) {
            return true;
        }
        return deliveryMode == DeliveryMode.REWORK;
    }

    /**
     * 现有 HTML 入口在渐进实现阶段也不能再退回整页重写。
     *
     * <p>这类文件已经具备稳定锚点，应该继续走内联脚本工作集、聚焦区块或 precise-html，
     * 而不是因为某次恢复策略收紧就重新回到 full-file 主路径。
     */
    public boolean canUseWholeFileRewriteForHtml(Path projectPath, Path relativePath, DeliveryMode deliveryMode) {
        if (!ProjectPathSupport.isHtml(relativePath)) {
            return true;
        }
        if (!Files.exists(projectPath.resolve(relativePath))) {
            return true;
        }
        return deliveryMode == DeliveryMode.REWORK;
    }

    public boolean supportsLocalCodeEditing(Path relativePath, String existingContent) {
        if (!codeEditAdapter.supports(relativePath)) {
            return false;
        }
        return codeEditAdapter.supportsPreciseEditing(relativePath, existingContent);
    }
}
