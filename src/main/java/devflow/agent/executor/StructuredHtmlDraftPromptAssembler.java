package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 统一组装结构化 HTML 草稿主链的 prompt。
 *
 * <p>这条链只在新建 HTML 入口文件时启用，目标是先产出可组装的结构化草稿，
 * 而不是让协调器直接持有整段宿主 HTML 模板。
 */
final class StructuredHtmlDraftPromptAssembler {

    private StructuredHtmlDraftPromptAssembler() {
    }

    static PatchGenerationPrompt assemble(
            Path relativePath,
            HtmlRuntimeOwnershipContract runtimeContract,
            String planSummary,
            String taskPackageMarkdown,
            String coderContextMarkdown,
            String reason,
            String feedback,
            String targetedContext
    ) {
        String system = """
                你是资深前端工程师。请为新的 HTML 入口文件生成结构化页面草稿，不要直接输出完整 HTML 文档。
                你必须只返回一个 JSON 对象，格式如下：
                {
                  "documentTitle": "页面标题；可为 null",
                  "headHtml": "放在 <head> 内、<style> 之前的额外 HTML；可为 null",
                  "markupHtml": "放在 <main id=\\"app-root\\"> 内部的 HTML",
                  "styleCss": "放在 <style id=\\"app-style\\"> 内部的 CSS；可为 null",
                  "scriptJs": "放在 <script id=\\"app-script\\"> 内部的 JS；可为 null",
                  "bodyAppendHtml": "追加到 <body> 末尾的额外 HTML；可为 null"
                }

                规则：
                1. 只返回 JSON，不要解释，不要 markdown
                2. 不要输出完整 HTML 文档
                3. markupHtml 只包含 <main id=\\"app-root\\"> 的内部内容，不要再包一层 <main>
                4. styleCss 只包含纯 CSS，不要包 <style>
                5. scriptJs 只包含纯 JavaScript，不要包 <script>
                6. 优先交付最小可运行入口和最小可运行表面，不要把所有复杂逻辑塞进首轮入口文件
                7. 如果当前子任务只负责入口、表面或接线，请保留清晰扩展点，但不要在当前负责能力范围内留下 TODO、空实现或 no-op
                8. scriptJs 的首轮骨架必须优先生成“命名的顶层函数/类”和一个很薄的 bootstrap 调用，不要把复杂逻辑直接塞进匿名回调或大段 DOMContentLoaded 处理器
                9. 事件绑定、渲染、状态更新如果需要占位，请拆成独立命名函数，便于后续按符号继续增量修改
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

                当前相关文件上下文：
                %s
                """.formatted(
                planSummary,
                taskPackageMarkdown,
                coderContextMarkdown == null ? "" : coderContextMarkdown,
                relativePath,
                reason,
                feedback == null ? "" : feedback,
                targetedContext
        );
        return new PatchGenerationPrompt(system, user);
    }

    private static String runtimeContractGuidance(HtmlRuntimeOwnershipContract runtimeContract) {
        if (runtimeContract == null || !runtimeContract.active()) {
            return "";
        }
        if (runtimeContract.externalCompanion()) {
            return """

                    当前 runtime contract：
                    1. 新的 HTML 宿主不能内联主运行时
                    2. scriptJs 必须为 null
                    3. 宿主只负责接入这些 runtime 根脚本：%s
                    """.formatted(String.join(", ", runtimeContract.runtimePathStrings()));
        }
        return """

                当前 runtime contract：
                1. HTML 宿主继续持有主运行时
                2. scriptJs 应作为主入口脚本生成在宿主中
                """;
    }
}
