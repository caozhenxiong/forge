package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.editing.CodePreciseEditor;
import devflow.agent.editing.HtmlDocumentAssembler;
import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;

/**
 * 组装 patch-first 主链的运行时依赖。
 *
 * <p>这里聚焦 patch 生成、预算、apply、verify 和宿主/嵌入路由，不再混入文件级上下文与兼容逻辑。
 */
final class PatchRuntimeBuilder {

    PatchRuntimeComponents build(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TreeSitterSupport treeSitterSupport,
            HtmlPreciseEditor htmlPreciseEditor,
            CodePreciseEditor codePreciseEditor,
            HtmlDocumentAssembler htmlDocumentAssembler,
            GenerationEngine generationEngine,
            int maxFileGenerationAttempts
    ) {
        HtmlFocusedRegionResolver htmlFocusedRegionResolver = new HtmlFocusedRegionResolver(treeSitterSupport);
        TargetLocator targetLocator = new TreeSitterTargetLocator(treeSitterSupport);
        EditUnitPlanner editUnitPlanner = new EditUnitPlanner(targetLocator);
        PatchFailureRouter patchFailureRouter = new PatchFailureRouter();
        PatchBudgetSettings patchBudgetSettings = PatchBudgetSettings.defaults();
        PatchBudgetPolicy patchBudgetPolicy = new PatchBudgetPolicy(patchBudgetSettings);
        PatchUnitSizer patchUnitSizer = new PatchUnitSizer(
                patchBudgetPolicy,
                patchFailureRouter,
                patchBudgetSettings
        );
        InlineScriptExtractToFileStrategy inlineScriptExtractToFileStrategy =
                new InlineScriptExtractToFileStrategy(htmlPreciseEditor);
        EmbeddingAdapter<InlineScriptEditPlan> inlineScriptEmbeddingAdapter =
                new HtmlInlineScriptEmbeddingAdapter(
                        treeSitterSupport,
                        editUnitPlanner,
                        patchUnitSizer,
                        htmlPreciseEditor
                );
        EmbeddingAdapter<InlineStyleEditPlan> inlineStyleEmbeddingAdapter =
                new HtmlInlineStyleEmbeddingAdapter(
                        treeSitterSupport,
                        editUnitPlanner,
                        patchUnitSizer,
                        htmlPreciseEditor
                );
        HtmlEditRoutingPolicy htmlEditRoutingPolicy = new HtmlEditRoutingPolicy(
                inlineScriptEmbeddingAdapter,
                inlineStyleEmbeddingAdapter,
                htmlFocusedRegionResolver
        );
        GeneratedContentGate generatedContentGate = new GeneratedContentGate(workspace, treeSitterSupport);
        StructuredPayloadReader structuredPayloadReader = new StructuredPayloadReader(objectMapper);
        GeneratedPayloadSupport generatedPayloadSupport = new GeneratedPayloadSupport(structuredPayloadReader);
        FocusedHtmlRegionNormalizer focusedHtmlRegionNormalizer =
                new FocusedHtmlRegionNormalizer(generatedPayloadSupport);
        FileGenerationFailureFactory fileGenerationFailureFactory = new FileGenerationFailureFactory();
        ImplementationGenerationObserverFactory implementationGenerationObserverFactory =
                new ImplementationGenerationObserverFactory();
        PatchExecutionSupport patchExecutionSupport = new PatchExecutionSupport(
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory
        );
        PatchRepairSettings patchRepairSettings = new PatchRepairSettings();
        PatchRepairClassifier patchRepairClassifier = new PatchRepairClassifier();
        PatchPayloadRepairSupport patchPayloadRepairSupport = new PatchPayloadRepairSupport(
                generatedPayloadSupport,
                new DeterministicJsonPayloadRepairer(),
                new ModelJsonRepairTurn(llmProvider, patchRepairSettings),
                patchRepairSettings,
                patchRepairClassifier,
                patchExecutionSupport
        );
        HostHtmlPatchExecutor hostHtmlPatchExecutor = new HostHtmlPatchExecutor(
                llmProvider,
                htmlPreciseEditor,
                htmlDocumentAssembler,
                generationEngine,
                generatedContentGate,
                generatedPayloadSupport,
                patchPayloadRepairSupport,
                focusedHtmlRegionNormalizer,
                htmlFocusedRegionResolver,
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory,
                maxFileGenerationAttempts
        );
        PatchVerifier patchVerifier = new PatchVerifier(treeSitterSupport, generatedContentGate);
        CodePatchKernel codePatchKernel = new CodePatchKernel(patchVerifier);
        LanguageEditAdapter codeEditAdapter =
                new TreeSitterCodeEditAdapter(targetLocator, codePreciseEditor, codePatchKernel);
        PatchContextBuilder patchContextBuilder = new PatchContextBuilder(targetLocator, codeEditAdapter);
        SyntaxRepairSupport syntaxRepairSupport = new SyntaxRepairSupport(
                patchRepairClassifier,
                new DeterministicSyntaxRepairer(),
                new SyntaxRepairTurn(llmProvider, patchRepairSettings),
                patchRepairSettings,
                patchVerifier,
                new RepairDiffScopeValidator(),
                patchExecutionSupport
        );
        EmbeddedPatchExecutor embeddedPatchExecutor = new EmbeddedPatchExecutor(
                llmProvider,
                generationEngine,
                generatedPayloadSupport,
                generatedContentGate,
                codePatchKernel,
                patchFailureRouter,
                patchBudgetPolicy,
                patchPayloadRepairSupport,
                syntaxRepairSupport,
                patchContextBuilder,
                inlineScriptExtractToFileStrategy,
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory,
                maxFileGenerationAttempts
        );
        PreciseCodePatchExecutor preciseCodePatchExecutor = new PreciseCodePatchExecutor(
                llmProvider,
                generationEngine,
                generatedPayloadSupport,
                codeEditAdapter,
                patchFailureRouter,
                patchBudgetPolicy,
                patchPayloadRepairSupport,
                syntaxRepairSupport,
                editUnitPlanner,
                patchContextBuilder,
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory,
                maxFileGenerationAttempts
        );
        WholeFilePatchExecutor wholeFilePatchExecutor = new WholeFilePatchExecutor(
                llmProvider,
                generationEngine,
                generatedContentGate,
                generatedPayloadSupport,
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory,
                maxFileGenerationAttempts
        );
        FileEditStrategyResolver fileEditStrategyResolver = new FileEditStrategyResolver(
                htmlPreciseEditor,
                codeEditAdapter
        );
        return new PatchRuntimeComponents(
                generatedContentGate,
                focusedHtmlRegionNormalizer,
                htmlFocusedRegionResolver,
                wholeFilePatchExecutor,
                hostHtmlPatchExecutor,
                embeddedPatchExecutor,
                preciseCodePatchExecutor,
                htmlEditRoutingPolicy,
                fileEditStrategyResolver,
                patchContextBuilder
        );
    }
}
