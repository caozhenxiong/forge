package devflow.agent.executor;

import devflow.agent.parsing.ByteRange;
import devflow.agent.parsing.HtmlEditableStructure;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.util.ProjectPathSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 从 HTML 文档中提取内联脚本工作集。
 * <p>
 * 这里不做业务推断，只回答一件事：
 * 当前 HTML 是否已经暴露了稳定的 script 锚点，并且这段脚本是否适合按代码符号继续精确编辑。
 */
class HtmlInlineScriptWorkingSetResolver {

    private final TreeSitterSupport treeSitterSupport;
    private final TargetLocator targetLocator;

    HtmlInlineScriptWorkingSetResolver(TreeSitterSupport treeSitterSupport, TargetLocator targetLocator) {
        this.treeSitterSupport = treeSitterSupport;
        this.targetLocator = targetLocator;
    }

    InlineScriptWorkingSet resolve(Path htmlPath, String htmlSource) {
        HtmlEditableStructure structure = treeSitterSupport.inspectEditableHtml(htmlSource);
        ByteRange scriptRange = structure.appScriptInnerRange();
        if (scriptRange == null || !scriptRange.isValid()) {
            return null;
        }
        String scriptContent = extractUtf8Range(htmlSource, scriptRange).strip();
        if (scriptContent.isBlank()) {
            return null;
        }
        Path syntheticPath = ProjectPathSupport.inlineScriptSyntheticPath(htmlPath);
        PatchTargetContext targetContext = targetLocator.locate(syntheticPath, scriptContent);
        if (!targetContext.preciseEditingSupported()) {
            return null;
        }
        // 如果脚本里只有局部变量这类“可替换但不可插入”的符号，
        // working-set 精确编辑会很不稳定，容易直接退化成 SYMBOL_NOT_FOUND。
        // 这类脚本更适合走 script 区块级改写，而不是伪装成符号级编辑。
        if (!targetContext.hasInsertableTargets()) {
            return null;
        }
        return new InlineScriptWorkingSet(syntheticPath, scriptContent);
    }

    private String extractUtf8Range(String source, ByteRange range) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        int start = Math.max(0, Math.min(range.startByte(), bytes.length));
        int end = Math.max(start, Math.min(range.endByte(), bytes.length));
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }
}
