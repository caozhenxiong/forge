package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 统一组装宿主 HTML patch 的生成 prompt。
 *
 * <p>HTML 宿主级 patch 目前有两条稳定主链：
 * 1. `precise-html`
 * 2. `focused-html-region`
 *
 * <p>这层把两类 prompt 都从协调器中抽离，避免宿主 HTML 模板继续散落。
 */
final class HtmlPatchPromptAssembler {

    private HtmlPatchPromptAssembler() {
    }

    static PatchGenerationPrompt preciseHtmlPrompt(
            Path relativePath,
            HtmlRuntimeOwnershipContract runtimeContract,
            String planSummary,
            String taskPackageMarkdown,
            String coderContextMarkdown,
            String reason,
            String feedback,
            String anchorSummary,
            String targetedContext,
            String summarizedHtml
    ) {
        String system = """
                你是资深前端工程师。请对现有 HTML 页面做“精确改写”。
                当前任务要求使用结构化精确改写协议，不要输出完整页面。
                你必须只返回一个 JSON 对象，格式如下：
                {
                  "markupHtml": "替换 <main id=\\"app-root\\"> 内部结构；无修改则为 null",
                  "styleCss": "替换 <style id=\\"app-style\\"> 内部 CSS；无修改则为 null",
                  "scriptJs": "替换 <script id=\\"app-script\\"> 内部 JS；无修改则为 null",
                  "headAppendHtml": "追加到 <head> 末尾、</head> 前的 HTML 片段；无修改则为 null",
                  "bodyAppendHtml": "追加到 <body> 末尾的 HTML 片段；无修改则为 null"
                }

                规则：
                1. 只返回 JSON，不要解释，不要 markdown
                2. 只返回需要修改的区块；未修改的字段必须为 null
                3. 不要直接输出完整 HTML 文档
                4. 如需新增资源接线，优先使用 headAppendHtml / bodyAppendHtml
                5. 如果脚本、样式或资源接线已经存在，不要重复追加，保持对应字段为 null
                6. 返回内容必须保证 HTML、脚本和样式都可解析
                """ + runtimeContractGuidance(runtimeContract);
        String user = """
                总体实现摘要：
                %s

                当前任务包：
                %s

                编码角色上下文切片：
                %s

                文件路径：
                %s

                变更原因：
                %s

                上一轮反馈：
                %s

                当前精确改写锚点：
                %s

                当前相关文件上下文：
                %s

                当前 HTML 内容：
                %s
                """.formatted(
                planSummary,
                taskPackageMarkdown,
                coderContextMarkdown == null ? "" : coderContextMarkdown,
                relativePath,
                reason,
                feedback == null ? "" : feedback,
                anchorSummary,
                targetedContext,
                summarizedHtml
        );
        return new PatchGenerationPrompt(system, user);
    }

    static PatchGenerationPrompt focusedRegionPrompt(
            Path relativePath,
            HtmlEditRegion region,
            HtmlRuntimeOwnershipContract runtimeContract,
            String planSummary,
            String taskPackageMarkdown,
            String coderContextMarkdown,
            String reason,
            String feedback,
            String targetedContext,
            String summarizedHtml
    ) {
        String system = focusedRegionSystemPrompt(region) + runtimeContractGuidance(runtimeContract);
        String user = """
                总体实现摘要：
                %s

                当前任务包：
                %s

                编码角色上下文切片：
                %s

                文件路径：
                %s

                当前聚焦区块：
                - region: %s
                - reason: %s

                上一轮反馈：
                %s

                当前相关文件上下文：
                %s

                当前 HTML 内容：
                %s
                """.formatted(
                planSummary,
                taskPackageMarkdown,
                coderContextMarkdown == null ? "" : coderContextMarkdown,
                relativePath,
                region,
                reason,
                feedback == null ? "" : feedback,
                targetedContext,
                summarizedHtml
        );
        return new PatchGenerationPrompt(system, user);
    }

    /**
     * 显式分支避免编译器生成 `$1` 合成类，减小增量编译和热替换时的脆弱面。
     */
    private static String runtimeContractGuidance(HtmlRuntimeOwnershipContract runtimeContract) {
        if (runtimeContract == null || !runtimeContract.active()) {
            return "";
        }
        if (runtimeContract.externalCompanion()) {
            return """

                    当前 runtime contract：
                    1. 宿主 HTML 只保留最小 bootstrapping，不再承载主运行时
                    2. scriptJs 必须保持为 null，不能回填 app-script 主逻辑
                    3. 必须只接入这些 runtime 根脚本：%s
                    4. 不允许保留 app-script 锚点或非空内联主脚本
                    """.formatted(String.join(", ", runtimeContract.runtimePathStrings()));
        }
        return """

                当前 runtime contract：
                1. 宿主 HTML 继续持有主运行时
                2. 不要新增 external runtime script 作为新的主入口
                """;
    }

    private static String focusedRegionSystemPrompt(HtmlEditRegion region) {
        if (region == HtmlEditRegion.SCRIPT) {
            return """
                    你是资深前端工程师。请只改写 HTML 中 <script id="app-script"> 的内部 JavaScript。
                    只输出该 script 区块的完整 JavaScript 内容，不要输出 HTML，不要解释，不要 markdown。
                    """;
        }
        if (region == HtmlEditRegion.STYLE) {
            return """
                    你是资深前端工程师。请只改写 HTML 中 <style id="app-style"> 的内部 CSS。
                    只输出该 style 区块的完整 CSS 内容，不要输出 HTML，不要解释，不要 markdown。
                    """;
        }
        return """
                你是资深前端工程师。请只改写 HTML 中 <main id="app-root"> 的内部结构。
                只输出该 main 区块的完整内部 HTML，不要输出完整页面，不要解释，不要 markdown。
                """;
    }
}
