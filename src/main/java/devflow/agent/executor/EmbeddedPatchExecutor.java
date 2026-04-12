package devflow.agent.executor;

import devflow.agent.editing.FileStateLedger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

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
    private final FileStateLedger fileStateLedger;

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
        this.fileStateLedger = new FileStateLedger();
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
        LinkedList<EditUnit> pendingUnits = initialPendingUnits(
                request.relativePath(),
                patchKind,
                patchPlan,
                patchProgressState
        );
        String currentContent = initialContent(
                initialContent,
                patchProgressState,
                request.relativePath(),
                patchKind
        );
        ArrayList<String> completedUnitLabels = initialCompletedUnitLabels(patchProgressState);
        while (!pendingUnits.isEmpty()) {
            EditUnit unit = pendingUnits.removeFirst();
            try {
                currentContent = executeUnit(request, patchKind, currentContent, unit);
                completedUnitLabels.add(unit.label());
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
                            fileStateLedger.capture(patchKind.syntheticPath(request.relativePath()), currentContent).contentHash(),
                            List.copyOf(new LinkedHashSet<>(completedUnitLabels)),
                            unit.label()
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
            FilePatchProgressState patchProgressState
    ) {
        List<EditUnit> freshUnits = patchPlan == null ? List.of() : patchPlan.units();
        if (patchProgressState != null
                && patchProgressState.matches(relativePath, patchKind.strategyName())
                && patchProgressState.resumable()) {
            return new LinkedList<>(resumeUnits(freshUnits, patchProgressState));
        }
        return new LinkedList<>(freshUnits);
    }

    private String initialContent(
            String initialContent,
            FilePatchProgressState patchProgressState,
            java.nio.file.Path relativePath,
            EmbeddedPatchKind patchKind
    ) {
        if (patchProgressState != null
                && patchProgressState.matches(relativePath, patchKind.strategyName())
                && patchProgressState.workingContent() != null
                && !patchProgressState.workingContent().isBlank()) {
            return patchProgressState.workingContent();
        }
        return initialContent;
    }

    private ArrayList<String> initialCompletedUnitLabels(FilePatchProgressState patchProgressState) {
        if (patchProgressState == null || patchProgressState.completedUnitLabels() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(patchProgressState.completedUnitLabels());
    }

    private List<EditUnit> resumeUnits(List<EditUnit> freshUnits, FilePatchProgressState patchProgressState) {
        if (freshUnits == null || freshUnits.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> completed = new LinkedHashSet<>(patchProgressState.completedUnitLabels());
        List<EditUnit> remaining = freshUnits.stream()
                .filter(unit -> unit != null && !completed.contains(unit.label()))
                .toList();
        if (patchProgressState.currentUnitLabel().isBlank()) {
            return remaining;
        }
        int resumeIndex = -1;
        for (int index = 0; index < remaining.size(); index++) {
            if (patchProgressState.currentUnitLabel().equals(remaining.get(index).label())) {
                resumeIndex = index;
                break;
            }
        }
        if (resumeIndex < 0) {
            return remaining;
        }
        return remaining.subList(resumeIndex, remaining.size());
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
