package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.ByteRange;
import devflow.agent.parsing.HtmlEditableStructure;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.util.ProjectPathSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 从 HTML 文档中提取内联样式工作集。
 *
 * <p>这层只回答一件事：
 * 当前 HTML 是否已经暴露了稳定的 style 锚点，并且这段样式是否适合按规则块继续精确编辑。
 */
class HtmlInlineStyleWorkingSetResolver {

    private final TreeSitterSupport treeSitterSupport;
    private final TargetLocator targetLocator;

    HtmlInlineStyleWorkingSetResolver(TreeSitterSupport treeSitterSupport, TargetLocator targetLocator) {
        this.treeSitterSupport = treeSitterSupport;
        this.targetLocator = targetLocator;
    }

    InlineStyleWorkingSet resolve(Path htmlPath, String htmlSource) {
        HtmlEditableStructure structure = treeSitterSupport.inspectEditableHtml(htmlSource);
        ByteRange styleRange = structure.appStyleInnerRange();
        if (styleRange == null || !styleRange.isValid()) {
            return null;
        }
        String styleContent = extractUtf8Range(htmlSource, styleRange).strip();
        if (styleContent.isBlank()) {
            return null;
        }
        Path syntheticPath = ProjectPathSupport.inlineStyleSyntheticPath(htmlPath);
        PatchTargetContext targetContext = targetLocator.locate(syntheticPath, styleContent);
        if (!targetContext.preciseEditingSupported()) {
            return null;
        }
        return new InlineStyleWorkingSet(
                syntheticPath,
                styleContent,
                targetContext.insertableTargetNames().size(),
                targetContext.targetNames().size()
        );
    }

    private String extractUtf8Range(String source, ByteRange range) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        int start = Math.max(0, Math.min(range.startByte(), bytes.length));
        int end = Math.max(start, Math.min(range.endByte(), bytes.length));
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }
}
