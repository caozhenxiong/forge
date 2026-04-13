package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;

/**
 * 统一渲染结构化 HTML 草稿主链的重试/终止反馈。
 */
public final class StructuredHtmlDraftFeedbackRenderer {

    private StructuredHtmlDraftFeedbackRenderer() {
    }

    public static String validationRetryFeedback(int attempt, Path relativePath, String validationFailure) {
        return """
                - attempt: %d
                - 文件: %s
                - 问题: %s
                要求：
                1. 继续返回结构化 HTML 草稿 JSON，不要输出完整 HTML
                2. markupHtml/styleCss/scriptJs 只填写对应区块内容
                3. 产物必须能组装成完整 HTML 文档，并保证内联脚本可解析
                4. scriptJs 保持“命名顶层函数 + 薄 bootstrap”结构，不要退化成匿名大回调
                """.formatted(attempt, relativePath, validationFailure);
    }

    public static String generationFailureAdvice() {
        return "请继续使用结构化 HTML 草稿 JSON；若需要额外头部或尾部片段，请使用 headHtml/bodyAppendHtml，而不是输出半截完整文档。";
    }
}
