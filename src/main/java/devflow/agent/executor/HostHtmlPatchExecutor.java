package devflow.agent.executor;

import devflow.agent.editing.HtmlDocumentAssembler;
import devflow.agent.editing.HtmlPreciseEditor;

/**
 * 宿主 HTML patch 主链执行器。
 *
 * <p>它负责三条宿主级 HTML 编辑路径：
 * 1. `structured-html`
 * 2. `precise-html`
 * 3. `focused-html-region`
 *
 * <p>这样 `FileEditCoordinator` 只保留文件级路由和事务写盘，
 * 不再继续内联宿主 HTML 的生成、apply、verify、失败装配逻辑。
 */
final class HostHtmlPatchExecutor {

    private final StructuredHtmlPatchExecutor structuredHtmlPatchExecutor;
    private final PreciseHtmlPatchExecutor preciseHtmlPatchExecutor;
    private final FocusedRegionHtmlPatchExecutor focusedRegionHtmlPatchExecutor;

    HostHtmlPatchExecutor(
            LlmProvider llmProvider,
            HtmlPreciseEditor htmlPreciseEditor,
            HtmlDocumentAssembler htmlDocumentAssembler,
            GenerationEngine generationEngine,
            GeneratedContentGate generatedContentGate,
            GeneratedPayloadSupport generatedPayloadSupport,
            PatchPayloadRepairSupport patchPayloadRepairSupport,
            HtmlFocusedRegionResolver htmlFocusedRegionResolver,
            FileGenerationFailureFactory fileGenerationFailureFactory,
            ImplementationGenerationObserverFactory implementationGenerationObserverFactory,
            int maxFileGenerationAttempts
    ) {
        PatchExecutionSupport executionSupport = new PatchExecutionSupport(
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory
        );
        ExternalizedRuntimeHostNormalizer externalizedRuntimeHostNormalizer = new ExternalizedRuntimeHostNormalizer();
        this.structuredHtmlPatchExecutor = new StructuredHtmlPatchExecutor(
                llmProvider,
                htmlDocumentAssembler,
                generationEngine,
                generatedContentGate,
                generatedPayloadSupport,
                maxFileGenerationAttempts,
                executionSupport
        );
        this.preciseHtmlPatchExecutor = new PreciseHtmlPatchExecutor(
                llmProvider,
                htmlPreciseEditor,
                generationEngine,
                generatedContentGate,
                patchPayloadRepairSupport,
                externalizedRuntimeHostNormalizer,
                executionSupport
        );
        this.focusedRegionHtmlPatchExecutor = new FocusedRegionHtmlPatchExecutor(
                llmProvider,
                htmlPreciseEditor,
                generationEngine,
                generatedContentGate,
                patchPayloadRepairSupport,
                htmlFocusedRegionResolver,
                externalizedRuntimeHostNormalizer,
                maxFileGenerationAttempts,
                executionSupport
        );
    }

    String generateStructuredHtmlDocument(HostHtmlPatchRequest request) {
        return structuredHtmlPatchExecutor.generate(request);
    }

    String generatePreciseHtml(HostHtmlPatchRequest request) {
        return preciseHtmlPatchExecutor.generate(request);
    }

    String generateFocusedRegion(HostHtmlPatchRequest request, HtmlEditRegion preferredRegion) {
        return focusedRegionHtmlPatchExecutor.generate(request, preferredRegion);
    }
}
