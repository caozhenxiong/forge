package devflow.agent.executor;

import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 宿主内嵌脚本/样式的共享 patch 执行器。
 *
 * <p>它负责：
 * 1. 从嵌入适配器拿到 edit plan；
 * 2. 顺序执行 patch 单元；
 * 3. 在失败时根据结构化路由决定 split/retry/externalize；
 * 4. 回填宿主并做宿主级 HTML 校验。
 *
 * <p>它不负责：
 * 1. 决定当前文件是否应走宿主嵌入路径；
 * 2. 构造 targeted context 或 task package；
 * 3. 文件级事务写盘。
 */
final class EmbeddedPatchExecutor {
    private final PatchFailureRouter patchFailureRouter;
    private final InlineScriptExtractToFileStrategy inlineScriptExtractToFileStrategy;
    private final int maxFileGenerationAttempts;
    private final PatchExecutionSupport executionSupport;
    private final EmbeddedPatchUnitExecutor unitExecutor;
    private final EmbeddedPatchHostValidator hostValidator;

    EmbeddedPatchExecutor(
            LlmProvider llmProvider,
            GenerationEngine generationEngine,
            GeneratedPayloadSupport generatedPayloadSupport,
            GeneratedContentGate generatedContentGate,
            CodePatchKernel codePatchKernel,
            PatchFailureRouter patchFailureRouter,
            PatchBudgetPolicy patchBudgetPolicy,
            PatchPayloadRepairSupport patchPayloadRepairSupport,
            SyntaxRepairSupport syntaxRepairSupport,
            PatchContextBuilder patchContextBuilder,
            InlineScriptExtractToFileStrategy inlineScriptExtractToFileStrategy,
            FileGenerationFailureFactory fileGenerationFailureFactory,
            ImplementationGenerationObserverFactory implementationGenerationObserverFactory,
            int maxFileGenerationAttempts
    ) {
        this.patchFailureRouter = patchFailureRouter;
        this.inlineScriptExtractToFileStrategy = inlineScriptExtractToFileStrategy;
        this.maxFileGenerationAttempts = maxFileGenerationAttempts;
        this.executionSupport = new PatchExecutionSupport(
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory
        );
        this.unitExecutor = new EmbeddedPatchUnitExecutor(
                llmProvider,
                generationEngine,
                generatedPayloadSupport,
                codePatchKernel,
                patchFailureRouter,
                patchBudgetPolicy,
                patchPayloadRepairSupport,
                syntaxRepairSupport,
                patchContextBuilder,
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory,
                maxFileGenerationAttempts
        );
        this.hostValidator = new EmbeddedPatchHostValidator(
                generatedContentGate,
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory,
                maxFileGenerationAttempts
        );
    }

    <T extends EmbeddingEditPlan> GeneratedFileOutput generate(
            EmbeddingAdapter<T> embeddingAdapter,
            EmbeddedPatchRequest request,
            EmbeddedPatchKind patchKind,
            String unavailableEvidence,
            String unavailableAction,
            String validationAction,
            boolean allowExternalize
    ) {
        T editPlan = embeddingAdapter.buildEditPlan(request.relativePath(), request.existingContent());
        if (editPlan == null || !editPlan.isUsable()) {
            throw executionSupport.generationFailure(
                    request.relativePath(),
                    request.deliveryMode(),
                    patchKind.strategyName(),
                    maxFileGenerationAttempts,
                    GenerationFailureType.SYMBOL_NOT_FOUND,
                    unavailableEvidence,
                    unavailableAction
            );
        }
        EmbeddedPatchOutcome executionOutcome = executeUnits(
                request,
                patchKind,
                editPlan.patchPlan(),
                editPlan.embeddedContent(),
                allowExternalize
        );
        if (executionOutcome.externalizeToFile()) {
            return inlineScriptExtractToFileStrategy.externalize(
                    request.relativePath(),
                    request.existingContent(),
                    executionOutcome.content()
            );
        }
        return hostValidator.mergeAndValidate(
                embeddingAdapter,
                request,
                patchKind,
                executionOutcome.content(),
                validationAction
        );
    }

    GeneratedFileOutput externalizeExistingScript(
            EmbeddingAdapter<InlineScriptEditPlan> embeddingAdapter,
            EmbeddedPatchRequest request,
            String evidence
    ) {
        InlineScriptEditPlan editPlan = embeddingAdapter.buildEditPlan(request.relativePath(), request.existingContent());
        if (editPlan == null || !editPlan.isUsable()) {
            throw executionSupport.generationFailure(
                    request.relativePath(),
                    request.deliveryMode(),
                    FileEditStrategyNames.INLINE_SCRIPT_WORKSET,
                    maxFileGenerationAttempts,
                    GenerationFailureType.SYMBOL_NOT_FOUND,
                    evidence,
                    "请先暴露稳定的 HTML 主脚本工作集，再决定是否外提。"
            );
        }
        executionSupport.appendImplementationEvent(
                request.eventJournal(),
                ImplementationEventMessages.externalizeInlineScript(
                        request.relativePath(),
                        "quality-plan-externalize-inline-script",
                        "quality-plan-prefers-externalized-runtime-script"
                )
        );
        return inlineScriptExtractToFileStrategy.externalize(
                request.relativePath(),
                request.existingContent(),
                editPlan.embeddedContent()
        );
    }

    private EmbeddedPatchOutcome executeUnits(
            EmbeddedPatchRequest request,
            EmbeddedPatchKind patchKind,
            PatchPlan patchPlan,
            String initialContent,
            boolean allowExternalize
    ) {
        FilePatchProgressState patchProgressState = request.patchProgressState();
        boolean resumeCompatible = canResumePatchProgress(request.relativePath(), patchKind, patchPlan, patchProgressState);
        LinkedList<EditUnit> pendingUnits = initialPendingUnits(
                request.relativePath(),
                patchKind,
                patchPlan,
                patchProgressState,
                resumeCompatible
        );
        String currentContent = initialContent(
                initialContent,
                patchProgressState,
                request.relativePath(),
                patchKind,
                resumeCompatible
        );
        while (!pendingUnits.isEmpty()) {
            EditUnit unit = pendingUnits.removeFirst();
            try {
                currentContent = executeUnit(request, patchKind, currentContent, unit);
            } catch (GenerationFailureException exception) {
                PatchFailure patchFailure = PatchFailure.of(exception.report().failureType(), exception.report().evidence());
                List<EditUnit> splitUnits = patchFailureRouter.splitIfNeeded(unit, patchFailure);
                if (splitUnits.isEmpty()) {
                    if (allowExternalize
                            && patchKind == EmbeddedPatchKind.SCRIPT
                            && inlineScriptExtractToFileStrategy.shouldExternalize(
                                    request.relativePath(),
                                    unit,
                                    exception.report()
                            )) {
                        executionSupport.appendImplementationEvent(
                                request.eventJournal(),
                                ImplementationEventMessages.externalizeInlineScript(
                                        request.relativePath(),
                                        patchKind.strategyName(),
                                        exception.report().failureType()
                                )
                        );
                        return EmbeddedPatchOutcome.externalize(currentContent);
                    }
                    throw exception.withPatchProgressState(new FilePatchProgressState(
                            request.relativePath(),
                            patchKind.strategyName(),
                            currentContent,
                            remainingUnits(unit, pendingUnits)
                    ));
                }
                executionSupport.appendImplementationEvent(
                        request.eventJournal(),
                        ImplementationEventMessages.splitUnit(
                                request.relativePath(),
                                patchKind.strategyName(),
                                unit.label(),
                                exception.report().failureType()
                        )
                );
                pendingUnits.addAll(0, splitUnits);
            }
        }
        return EmbeddedPatchOutcome.contentOnly(currentContent);
    }

    private LinkedList<EditUnit> initialPendingUnits(
            java.nio.file.Path relativePath,
            EmbeddedPatchKind patchKind,
            PatchPlan patchPlan,
            FilePatchProgressState patchProgressState,
            boolean resumeCompatible
    ) {
        if (resumeCompatible && patchProgressState != null) {
            return new LinkedList<>(patchProgressState.pendingUnits());
        }
        return new LinkedList<>(patchPlan.units());
    }

    private String initialContent(
            String initialContent,
            FilePatchProgressState patchProgressState,
            java.nio.file.Path relativePath,
            EmbeddedPatchKind patchKind,
            boolean resumeCompatible
    ) {
        if (resumeCompatible
                && patchProgressState != null
                && patchProgressState.matches(relativePath, patchKind.strategyName())
                && patchProgressState.workingContent() != null
                && !patchProgressState.workingContent().isBlank()) {
            return patchProgressState.workingContent();
        }
        return initialContent;
    }

    /**
     * 旧 patchProgress 只能在“当前内容仍属于同一骨架”时复用。
     *
     * <p>这里不做兼容修补，也不尝试把旧骨架硬转成新骨架；结果只有两种：
     * 1. 仍与 fresh plan 同骨架，继续；
     * 2. 已失配，直接丢弃旧 progress，回到 fresh plan。
     */
    private boolean canResumePatchProgress(
            java.nio.file.Path relativePath,
            EmbeddedPatchKind patchKind,
            PatchPlan patchPlan,
            FilePatchProgressState patchProgressState
    ) {
        if (patchProgressState == null
                || !patchProgressState.matches(relativePath, patchKind.strategyName())
                || !patchProgressState.resumable()
                || patchPlan == null
                || patchPlan.units().isEmpty()) {
            return false;
        }
        Set<EditUnitKind> freshKinds = new LinkedHashSet<>();
        Set<String> freshSymbols = new LinkedHashSet<>();
        boolean freshAllowsAppendOnly = false;
        for (EditUnit freshUnit : patchPlan.units()) {
            if (freshUnit == null) {
                continue;
            }
            freshKinds.add(freshUnit.kind());
            if (freshUnit.appendOnly()) {
                freshAllowsAppendOnly = true;
                continue;
            }
            freshSymbols.addAll(freshUnit.allowedSymbols());
        }
        if (freshKinds.isEmpty()) {
            return false;
        }
        for (EditUnit pendingUnit : patchProgressState.pendingUnits()) {
            if (pendingUnit == null || !freshKinds.contains(pendingUnit.kind())) {
                return false;
            }
            if (pendingUnit.appendOnly()) {
                if (!freshAllowsAppendOnly) {
                    return false;
                }
                continue;
            }
            if (!freshSymbols.containsAll(pendingUnit.allowedSymbols())) {
                return false;
            }
        }
        return true;
    }

    private List<EditUnit> remainingUnits(EditUnit failedUnit, LinkedList<EditUnit> pendingUnits) {
        LinkedList<EditUnit> remaining = new LinkedList<>();
        remaining.add(failedUnit);
        remaining.addAll(pendingUnits);
        return List.copyOf(remaining);
    }

    private String executeUnit(
            EmbeddedPatchRequest request,
            EmbeddedPatchKind patchKind,
            String currentContent,
            EditUnit unit
    ) {
        return unitExecutor.execute(request, patchKind, currentContent, unit);
    }
}
