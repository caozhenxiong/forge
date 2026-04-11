package devflow.agent.executor;

import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;

/**
 * 统一组装整文件重写主链的 prompt。
 *
 * <p>虽然整文件重写已经不是默认主路径，但在允许使用 whole-file 的极少数场景里，
 * prompt 仍然应该从协调器中抽离，避免门面类继续堆积模板字面量。
 */
final class WholeFilePromptAssembler {

    private WholeFilePromptAssembler() {
    }

    static PatchGenerationPrompt assemble(
            Path relativePath,
            DeliveryMode deliveryMode,
            HtmlRuntimeOwnershipContract runtimeContract,
            String planSummary,
            String taskPackageMarkdown,
            String reason,
            String feedback,
            String targetedContext,
            String existingContent
    ) {
        String system = """
                你是资深软件工程师。请只输出目标文件的完整最终内容。
                不要解释，不要 markdown 代码块，不要补充额外文字。
                """;
        if (deliveryMode == DeliveryMode.SKELETON) {
            system = system + """

                    当前处于骨架模式：
                    1. 只建立最小可运行骨架，不要一次塞入全部复杂逻辑
                    2. 页面/程序必须可打开、可自检、结构合法
                    3. 优先输出清晰的入口结构、基础样式和运行入口
                    4. 可以预留稳定扩展点，但不要把本子任务验收范围内的行为写成空实现、no-op 或 TODO
                    5. 不要为了追求完整而让单文件过长
                    """;
        } else if (deliveryMode == DeliveryMode.INCREMENTAL) {
            system = system + """

                    当前处于渐进填充模式：
                    1. 只补当前子任务的功能，不要扩展到无关能力
                    2. 优先在已有骨架上增量添加函数、状态和事件，不要重写整套结构
                    3. 保持已有入口、样式、模块边界不变
                    4. 单次改动要尽量小，避免大段推翻式改写
                    """;
        }
        if (ProjectPathSupport.isHtml(relativePath)) {
            system = system + """

                    HTML 入口文件约束：
                    1. 请优先输出可持续增量修改的结构
                    2. 主内容容器使用 <main id="app-root">...</main>
                    3. 内联样式使用 <style id="app-style">...</style>
                    4. 主脚本使用 <script id="app-script">...</script>
                    5. 后续精确改写会依赖这些稳定锚点，请保持这些 id 不变
                    """;
            if (runtimeContract != null && runtimeContract.active() && runtimeContract.externalCompanion()) {
                system = system + """

                        当前 runtime contract：
                        1. 宿主 HTML 不得承载主运行时
                        2. 不要输出 app-script 主逻辑
                        3. 只能接入这些 runtime 根脚本：%s
                        """.formatted(String.join(", ", runtimeContract.runtimePathStrings()));
            }
        }
        String user = """
                总体实现摘要：
                %s

                当前任务包：
                %s

                当前目标文件：
                - 路径：%s
                - 变更原因：%s

                上一轮反馈：
                %s

                当前相关文件上下文：
                %s

                当前内容：
                %s

                请输出该文件修改后的完整内容。
                """.formatted(
                planSummary,
                taskPackageMarkdown,
                relativePath,
                reason,
                feedback == null ? "" : feedback,
                targetedContext,
                existingContent
        );
        return new PatchGenerationPrompt(system, user);
    }
}
