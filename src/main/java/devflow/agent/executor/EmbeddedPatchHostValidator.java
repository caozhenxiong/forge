package devflow.agent.executor;

/**
 * 宿主内嵌 patch 完成后的宿主 HTML 回填与校验器。
 *
 * <p>执行器只负责 patch 单元本身，宿主回填和宿主级校验放在这里统一处理，
 * 避免 `EmbeddedPatchExecutor` 同时承载两条职责。
 */
final class EmbeddedPatchHostValidator {

    private final GeneratedContentGate generatedContentGate;
    private final PatchExecutionSupport executionSupport;
    private final int maxFileGenerationAttempts;

    EmbeddedPatchHostValidator(
            GeneratedContentGate generatedContentGate,
            FileGenerationFailureFactory fileGenerationFailureFactory,
            ImplementationGenerationObserverFactory implementationGenerationObserverFactory,
            int maxFileGenerationAttempts
    ) {
        this.generatedContentGate = generatedContentGate;
        this.executionSupport = new PatchExecutionSupport(
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory
        );
        this.maxFileGenerationAttempts = maxFileGenerationAttempts;
    }

    <T extends EmbeddingEditPlan> GeneratedFileOutput mergeAndValidate(
            EmbeddingAdapter<T> embeddingAdapter,
            EmbeddedPatchRequest request,
            EmbeddedPatchKind patchKind,
            String embeddedContent,
            String validationAction
    ) {
        String mergedHtml = embeddingAdapter.mergeIntoHost(request.existingContent(), embeddedContent);
        GateReport validationReport = generatedContentGate.evaluate(
                new GeneratedContentGateInput(request.projectPath(), request.relativePath(), mergedHtml)
        );
        if (!validationReport.passed()) {
            String validationFailure = generatedContentGate.renderFailure(validationReport);
            throw executionSupport.generationFailure(
                    request.relativePath(),
                    request.deliveryMode(),
                    patchKind.strategyName(),
                    maxFileGenerationAttempts,
                    generatedContentGate.failureTypeFor(validationReport),
                    validationFailure,
                    validationAction
            );
        }
        return GeneratedFileOutput.primaryOnly(mergedHtml);
    }
}
