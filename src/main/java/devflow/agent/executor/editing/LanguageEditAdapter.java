package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.ExactReplaceEdit;
import java.nio.file.Path;

/**
 * 代码语言的轻量 patch 适配器。
 *
 * <p>这层不做大而全的语言专属核心，只统一三件事：
 * 1. 当前语言是否支持进入 patch 主链；
 * 2. patch 目标如何定位与描述；
 * 3. patch 在本地如何 normalize / apply / verify。
 */
public interface LanguageEditAdapter {

    public boolean supports(Path relativePath);

    /**
     * 判断当前源码是否已经具备稳定的局部编辑锚点。
     *
     * <p>这层返回的是“能否进入 patch-first 主链”，而不是“这次模型一定能改成功”。
     */
    public boolean supportsPreciseEditing(Path relativePath, String source);

    /**
     * 判断当前文件是否允许走 append-only 引导路径。
     *
     * <p>它主要覆盖新文件、空文件、或只有最小骨架的场景，
     * 让系统仍然可以通过局部追加建立可编辑锚点，而不是回退成整文件重写。
     */
    public boolean supportsAppendOnlyEditing(Path relativePath, String source);

    public PatchTargetContext locateTargets(Path relativePath, String source);

    public PatchApplyResult applyPatch(Path projectPath, Path relativePath, String currentContent, ExactReplaceEdit edit);
}
