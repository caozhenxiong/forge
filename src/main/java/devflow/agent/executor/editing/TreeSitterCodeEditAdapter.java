package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.CodePreciseEditor;
import devflow.agent.editing.ExactReplaceEdit;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;

/**
 * 基于 tree-sitter 的代码语言适配器。
 *
 * <p>当前先覆盖 Forge 已进入 `precise-code` 主链的代码语言：
 * JS / TS / CSS / Python / Java / Go。
 * 这层不区分具体业务框架，只提供统一的 patch 入口。
 */
public final class TreeSitterCodeEditAdapter implements LanguageEditAdapter {

    private final TargetLocator targetLocator;
    private final CodePreciseEditor codePreciseEditor;
    private final CodePatchKernel codePatchKernel;

    public TreeSitterCodeEditAdapter(
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
            ExactReplaceEdit edit
    ) {
        return codePatchKernel.applyCodeFile(projectPath, relativePath, currentContent, edit);
    }
}
