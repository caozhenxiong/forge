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
 * <p>这样宿主 HTML 的生成、apply、verify、失败装配逻辑就和上层文件级路由分离，
 * 不再揉回同一个编排层。
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

    String generateStructuredHtmlDocument(HtmlTargetedRewriteRequest request) {
        return structuredHtmlPatchExecutor.generate(request);
    }

    String generatePreciseHtml(HtmlTargetedRewriteRequest request) {
        return preciseHtmlPatchExecutor.generate(request);
    }

    String generateFocusedRegion(HtmlTargetedRewriteRequest request, HtmlEditRegion preferredRegion) {
        return focusedRegionHtmlPatchExecutor.generate(request, preferredRegion);
    }
}
