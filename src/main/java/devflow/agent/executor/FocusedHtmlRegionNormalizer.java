package devflow.agent.executor;

import devflow.agent.editing.HtmlPrecisePatch;
import devflow.agent.editing.PreciseEditException;
import devflow.agent.editing.PreciseEditFailureReason;
import devflow.agent.parsing.HtmlDocumentInspector;
import java.nio.file.Path;

/**
 * 统一处理 focused-html-region 的结果归一。
 *
 * <p>聚焦区块模式要求模型只返回当前区块的“内部内容”，
 * 这层负责把包装标签剥掉并转换成宿主 HTML patch。
 */
final class FocusedHtmlRegionNormalizer {

    private final GeneratedPayloadSupport generatedPayloadSupport;

    FocusedHtmlRegionNormalizer(GeneratedPayloadSupport generatedPayloadSupport) {
        this.generatedPayloadSupport = generatedPayloadSupport;
    }

    HtmlPrecisePatch toPatch(HtmlEditRegion region, String generatedContent) {
        String content = generatedPayloadSupport.normalizeGeneratedPayload(Path.of("focused-region"), generatedContent).strip();
        if (region == HtmlEditRegion.SCRIPT) {
            return new HtmlPrecisePatch(null, null, normalizeFocusedScriptContent(content), null, null);
        }
        if (region == HtmlEditRegion.STYLE) {
            return new HtmlPrecisePatch(null, normalizeFocusedStyleContent(content), null, null, null);
        }
        return new HtmlPrecisePatch(normalizeFocusedMarkupContent(content), null, null, null, null);
    }

    private String normalizeFocusedScriptContent(String content) {
        String unwrapped = HtmlDocumentInspector.unwrapSingleWrappedElementBody(content, "script");
        if (unwrapped != null) {
            return unwrapped;
        }
        if (HtmlDocumentInspector.containsElementTag(content, "script")) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Focused script region must return only JavaScript body, not nested <script> tags."
            );
        }
        return content;
    }

    private String normalizeFocusedStyleContent(String content) {
        String unwrapped = HtmlDocumentInspector.unwrapSingleWrappedElementBody(content, "style");
        if (unwrapped != null) {
            return unwrapped;
        }
        if (HtmlDocumentInspector.containsElementTag(content, "style")) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Focused style region must return only CSS body, not nested <style> tags."
            );
        }
        return content;
    }

    private String normalizeFocusedMarkupContent(String content) {
        String unwrapped = HtmlDocumentInspector.unwrapSingleWrappedElementBody(content, "main");
        if (unwrapped != null) {
            return unwrapped;
        }
        return content;
    }
}
