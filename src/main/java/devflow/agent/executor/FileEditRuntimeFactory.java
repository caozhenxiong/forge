package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.editing.CodePreciseEditor;
import devflow.agent.editing.HtmlDocumentAssembler;
import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;

/**
 * 组装文件级 patch 运行时依赖。
 *
 * <p>这里负责把：
 * 1. 预算/路由策略；
 * 2. 语言适配器与嵌入适配器；
 * 3. patch 执行器；
 * 4. 文件级上下文支撑；
 * 组装成 `FileEditCoordinator` 需要的最小组件集合。
 *
 * <p>这样协调器不再继续承担“自己 new 整个 patch 主链”的职责。
 */
final class FileEditRuntimeFactory {

    FileEditRuntimeComponents create(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TreeSitterSupport treeSitterSupport,
            HtmlPreciseEditor htmlPreciseEditor,
            CodePreciseEditor codePreciseEditor,
            HtmlDocumentAssembler htmlDocumentAssembler,
            GenerationEngine generationEngine,
            RuntimeWorkingSetResolver runtimeWorkingSetResolver,
            int maxFileGenerationAttempts
    ) {
        PatchRuntimeComponents patchRuntime = new PatchRuntimeBuilder().build(
                llmProvider,
                workspace,
                objectMapper,
                treeSitterSupport,
                htmlPreciseEditor,
                codePreciseEditor,
                htmlDocumentAssembler,
                generationEngine,
                maxFileGenerationAttempts
        );
        return new FileRoutingRuntimeBuilder().build(
                workspace,
                runtimeWorkingSetResolver,
                patchRuntime,
                maxFileGenerationAttempts
        );
    }
}
