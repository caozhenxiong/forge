package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.List;

/**
 * 统一构建 patch 主链需要的目标上下文。
 *
 * <p>这层只负责从定位器/语言适配器中提炼稳定的结构化上下文：
 * 1. 当前文件可见的 patch 目标摘要；
 * 2. 可用于反馈收窄和重试裁剪的符号清单；
 * 3. append-only 单元的顶层符号计数。
 *
 * <p>它不直接拼 prompt，也不决定流程如何推进。
 */
public final class PatchContextBuilder {

    private final TargetLocator targetLocator;
    private final LanguageEditAdapter codeEditAdapter;

    public PatchContextBuilder(TargetLocator targetLocator, LanguageEditAdapter codeEditAdapter) {
        this.targetLocator = targetLocator;
        this.codeEditAdapter = codeEditAdapter;
    }

    public String describeCodeTargets(Path relativePath, String source) {
        return locateCodeTargets(relativePath, source).targetSummary();
    }

    public String describeInlineScriptTargets(Path hostRelativePath, String scriptContent) {
        return targetLocator.locate(ProjectPathSupport.inlineScriptSyntheticPath(hostRelativePath), scriptContent).targetSummary();
    }

    public String describeInlineStyleTargets(Path hostRelativePath, String styleContent) {
        return targetLocator.locate(ProjectPathSupport.inlineStyleSyntheticPath(hostRelativePath), styleContent).targetSummary();
    }

    public List<String> relevantCodeSymbols(Path relativePath, String source) {
        if (source == null || source.isBlank() || !codeEditAdapter.supports(relativePath)) {
            return List.of();
        }
        return codeEditAdapter.locateTargets(relativePath, source).targetNames();
    }

    public List<String> relevantInlineScriptSymbols(Path hostRelativePath, String scriptContent) {
        if (scriptContent == null || scriptContent.isBlank()) {
            return List.of();
        }
        return targetLocator.locate(ProjectPathSupport.inlineScriptSyntheticPath(hostRelativePath), scriptContent).targetNames();
    }

    public List<String> relevantInlineStyleSymbols(Path hostRelativePath, String styleContent) {
        if (styleContent == null || styleContent.isBlank()) {
            return List.of();
        }
        return targetLocator.locate(ProjectPathSupport.inlineStyleSyntheticPath(hostRelativePath), styleContent).targetNames();
    }

    public int topLevelCodeSymbolCount(Path relativePath, String source) {
        return relevantCodeSymbols(relativePath, source).size();
    }

    private PatchTargetContext locateCodeTargets(Path relativePath, String source) {
        if (!codeEditAdapter.supports(relativePath)) {
            return new PatchTargetContext(relativePath, null, false, List.of(), List.of(), "");
        }
        return codeEditAdapter.locateTargets(relativePath, source);
    }
}
