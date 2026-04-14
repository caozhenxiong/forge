package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;
import devflow.agent.editing.precise.HtmlEditRegion;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

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
public final class HtmlPatchPromptAssembler {

    private HtmlPatchPromptAssembler() {
    }

    public static PatchGenerationPrompt preciseHtmlPrompt(
            Path relativePath,
            HtmlRuntimeOwnershipContract runtimeContract,
            String planSummary,
            String taskPackageMarkdown,
            String coderContextMarkdown,
            String reason,
            String feedback,
            String anchorSummary,
            String targetedContext,
            String currentHtml
    ) {
        String targetPath = relativePath == null ? "unknown-file" : relativePath.toString().replace('\\', '/');
        var fileState = ExactReplacePromptSupport.capture(targetPath, currentHtml);
        String system = """
                你是资深前端工程师。请对现有 HTML 页面做精确的 exact replace 改写。
                %s

                额外规则：
                1. 只返回最小替换，不要直接输出完整 HTML 文档
                2. 如需新增资源接线，应通过替换已有锚点附近的最小片段完成
                3. 如果脚本、样式或资源接线已经存在，不要重复追加
                4. 返回内容必须保证 HTML、脚本和样式都可解析
                """.formatted(ExactReplacePromptSupport.exactReplaceProtocol("HTML 页面")) + runtimeContractGuidance(runtimeContract);
        String user = """
                总体实现摘要：
                %s

                当前任务包：
                %s

                编码角色上下文切片：
                %s

                文件路径：
                %s

                当前 HTML 状态：
                - targetPath: %s
                - contentHash: %s
                - lineCount: %s

                变更原因：
                %s

                上一轮反馈：
                %s

                当前精确改写锚点：
                %s

                当前相关文件上下文：
                %s

                当前 HTML 内容（必须原样引用 oldText）：
                %s
                """.formatted(
                planSummary,
                taskPackageMarkdown,
                coderContextMarkdown == null ? "" : coderContextMarkdown,
                relativePath,
                targetPath,
                fileState.contentHash(),
                fileState.lineCount(),
                reason,
                feedback == null ? "" : feedback,
                anchorSummary,
                targetedContext,
                ExactReplacePromptSupport.renderCurrentContent(currentHtml)
        );
        return new PatchGenerationPrompt(system, user);
    }

    public static PatchGenerationPrompt focusedRegionPrompt(
            Path relativePath,
            HtmlEditRegion region,
            HtmlRuntimeOwnershipContract runtimeContract,
            String planSummary,
            String taskPackageMarkdown,
            String coderContextMarkdown,
            String reason,
            String feedback,
            String targetedContext,
            String currentRegionContent
    ) {
        String targetPath = focusedRegionTargetPath(relativePath, region);
        var fileState = ExactReplacePromptSupport.capture(targetPath, currentRegionContent);
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
                - targetPath: %s
                - contentHash: %s
                - lineCount: %s
                - reason: %s

                上一轮反馈：
                %s

                当前相关文件上下文：
                %s

                当前聚焦区块内容（必须原样引用 oldText）：
                %s
                """.formatted(
                planSummary,
                taskPackageMarkdown,
                coderContextMarkdown == null ? "" : coderContextMarkdown,
                relativePath,
                region,
                targetPath,
                fileState.contentHash(),
                fileState.lineCount(),
                reason,
                feedback == null ? "" : feedback,
                targetedContext,
                ExactReplacePromptSupport.renderCurrentContent(currentRegionContent)
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
                    %s

                    额外规则：
                    1. `oldText` / `newText` 都只允许针对 script 内部内容
                    2. 不要输出 HTML，不要包裹 <script> 标签
                    """.formatted(ExactReplacePromptSupport.exactReplaceProtocol("script 区块"));
        }
        if (region == HtmlEditRegion.STYLE) {
            return """
                    你是资深前端工程师。请只改写 HTML 中 <style id="app-style"> 的内部 CSS。
                    %s

                    额外规则：
                    1. `oldText` / `newText` 都只允许针对 style 内部内容
                    2. 不要输出 HTML，不要包裹 <style> 标签
                    """.formatted(ExactReplacePromptSupport.exactReplaceProtocol("style 区块"));
        }
        return """
                你是资深前端工程师。请只改写 HTML 中 <main id="app-root"> 的内部结构。
                %s

                额外规则：
                1. `oldText` / `newText` 都只允许针对 main 内部内容
                2. 不要输出完整页面
                """.formatted(ExactReplacePromptSupport.exactReplaceProtocol("main 区块"));
    }

    private static String focusedRegionTargetPath(Path relativePath, HtmlEditRegion region) {
        String base = relativePath == null ? "unknown-file" : relativePath.toString().replace('\\', '/');
        String suffix = region == null ? "markup" : region.name().toLowerCase(java.util.Locale.ROOT);
        return base + "#" + suffix;
    }
}
