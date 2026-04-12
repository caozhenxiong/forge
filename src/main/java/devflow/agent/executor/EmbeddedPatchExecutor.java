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
            EmbeddedTargetedRewriteRequest request,
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
                    GenerationFailureType.TARGET_NOT_FOUND,
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
            EmbeddedTargetedRewriteRequest request,
            String evidence
    ) {
        InlineScriptEditPlan editPlan = embeddingAdapter.buildEditPlan(request.relativePath(), request.existingContent());
        if (editPlan == null || !editPlan.isUsable()) {
            throw executionSupport.generationFailure(
                    request.relativePath(),
                    request.deliveryMode(),
                    FileEditStrategyNames.INLINE_SCRIPT_WORKSET,
                    maxFileGenerationAttempts,
                    GenerationFailureType.TARGET_NOT_FOUND,
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
            EmbeddedTargetedRewriteRequest request,
            EmbeddedPatchKind patchKind,
            PatchPlan patchPlan,
            String initialContent,
            boolean allowExternalize
    ) {
        FileEditAttemptState editAttemptState = request.editAttemptState();
        LinkedList<EditUnit> pendingUnits = initialPendingUnits(
                request.relativePath(),
                patchKind,
                patchPlan,
                editAttemptState
        );
        String currentContent = initialContent(
                initialContent,
                editAttemptState,
                request.relativePath(),
                patchKind
        );
        ArrayList<String> completedTargetLabels = initialCompletedUnitLabels(editAttemptState);
        while (!pendingUnits.isEmpty()) {
            EditUnit unit = pendingUnits.removeFirst();
            try {
                currentContent = executeUnit(request, patchKind, currentContent, unit);
                completedTargetLabels.add(unit.label());
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
                    throw exception.withEditAttemptState(new FileEditAttemptState(
                            request.relativePath(),
                            FileEditProtocolNames.TARGETED_REWRITE,
                            patchKind.strategyName(),
                            currentContent,
                            fileStateLedger.capture(patchKind.syntheticPath(request.relativePath()), currentContent).contentHash(),
                            List.copyOf(new LinkedHashSet<>(completedTargetLabels)),
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
            FileEditAttemptState editAttemptState
    ) {
        List<EditUnit> freshUnits = patchPlan == null ? List.of() : patchPlan.units();
        if (editAttemptState != null
                && editAttemptState.matches(relativePath, patchKind.strategyName())
                && editAttemptState.resumable()) {
            return new LinkedList<>(resumeUnits(freshUnits, editAttemptState));
        }
        return new LinkedList<>(freshUnits);
    }

    private String initialContent(
            String initialContent,
            FileEditAttemptState editAttemptState,
            java.nio.file.Path relativePath,
            EmbeddedPatchKind patchKind
    ) {
        if (editAttemptState != null
                && editAttemptState.matches(relativePath, patchKind.strategyName())
                && editAttemptState.workingContent() != null
                && !editAttemptState.workingContent().isBlank()) {
            return editAttemptState.workingContent();
        }
        return initialContent;
    }

    private ArrayList<String> initialCompletedUnitLabels(FileEditAttemptState editAttemptState) {
        if (editAttemptState == null || editAttemptState.completedTargetLabels() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(editAttemptState.completedTargetLabels());
    }

    private List<EditUnit> resumeUnits(List<EditUnit> freshUnits, FileEditAttemptState editAttemptState) {
        if (freshUnits == null || freshUnits.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> completed = new LinkedHashSet<>(editAttemptState.completedTargetLabels());
        List<EditUnit> remaining = freshUnits.stream()
                .filter(unit -> unit != null && !completed.contains(unit.label()))
                .toList();
        if (editAttemptState.currentTargetLabel().isBlank()) {
            return remaining;
        }
        int resumeIndex = -1;
        for (int index = 0; index < remaining.size(); index++) {
            if (editAttemptState.currentTargetLabel().equals(remaining.get(index).label())) {
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
            EmbeddedTargetedRewriteRequest request,
            EmbeddedPatchKind patchKind,
            String currentContent,
            EditUnit unit
    ) {
        return unitExecutor.execute(request, patchKind, currentContent, unit);
    }
}
