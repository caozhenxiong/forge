package devflow.agent.executor;

import devflow.agent.editing.CodePreciseEditor;
import devflow.agent.editing.StructuredDiffPatch;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;

/**
 * 基于 tree-sitter 的代码语言适配器。
 *
 * <p>当前先覆盖 Forge 已进入 `precise-code` 主链的代码语言：
 * JS / TS / CSS / Python / Java / Go。
 * 这层不区分具体业务框架，只提供统一的 patch 入口。
 */
final class TreeSitterCodeEditAdapter implements LanguageEditAdapter {

    private final TargetLocator targetLocator;
    private final CodePreciseEditor codePreciseEditor;
    private final CodePatchKernel codePatchKernel;

    TreeSitterCodeEditAdapter(
            TargetLocator targetLocator,
            CodePreciseEditor codePreciseEditor,
            CodePatchKernel codePatchKernel
    ) {
        this.targetLocator = targetLocator;
        this.codePreciseEditor = codePreciseEditor;
        this.codePatchKernel = codePatchKernel;
    }

    @Override
    public boolean supports(Path relativePath) {
        return ProjectPathSupport.isPreciseCode(relativePath);
    }

    @Override
    public boolean supportsPreciseEditing(Path relativePath, String source) {
        return supports(relativePath) && targetLocator.locate(relativePath, source).preciseEditingSupported();
    }

    @Override
    public boolean supportsAppendOnlyEditing(Path relativePath, String source) {
        return supports(relativePath) && codePreciseEditor.supportsAppendOnlyEditing(relativePath, source);
    }

    @Override
    public PatchTargetContext locateTargets(Path relativePath, String source) {
        return targetLocator.locate(relativePath, source);
    }

    @Override
    public PatchApplyResult applyPatch(
            Path projectPath,
            Path relativePath,
            String currentContent,
            StructuredDiffPatch patch
    ) {
        return codePatchKernel.applyCodeFile(projectPath, relativePath, currentContent, patch);
    }
}
