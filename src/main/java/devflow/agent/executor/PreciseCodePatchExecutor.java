package devflow.agent.executor;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

/**
 * 代码文件 `precise-code` 主链执行器。
 *
 * <p>它负责：
 * 1. 生成代码 patch 单元计划；
 * 2. 顺序执行单元并在失败时拆分；
 * 3. 对空文件脚手架单元做后续符号扩展；
 * 4. 统一 apply/verify 和结构化失败反馈。
 *
 * <p>它不负责：
 * 1. 决定当前文件是否应该进入 `precise-code`；
 * 2. 生成 targeted context 或 task package；
 * 3. 文件级事务写盘。
 */
final class PreciseCodePatchExecutor {

    private final PatchFailureRouter patchFailureRouter;
    private final PatchBudgetPolicy patchBudgetPolicy;
    private final EditUnitPlanner editUnitPlanner;
    private final PatchContextBuilder patchContextBuilder;
    private final int maxFileGenerationAttempts;
    private final PatchExecutionSupport executionSupport;
    private final CodePatchUnitExecutor unitExecutor;
    private final CodeScaffoldExpansionPlanner scaffoldExpansionPlanner;

    PreciseCodePatchExecutor(
            LlmProvider llmProvider,
            GenerationEngine generationEngine,
            GeneratedPayloadSupport generatedPayloadSupport,
            LanguageEditAdapter codeEditAdapter,
            PatchFailureRouter patchFailureRouter,
            PatchBudgetPolicy patchBudgetPolicy,
            PatchPayloadRepairSupport patchPayloadRepairSupport,
            SyntaxRepairSupport syntaxRepairSupport,
            EditUnitPlanner editUnitPlanner,
            PatchContextBuilder patchContextBuilder,
            FileGenerationFailureFactory fileGenerationFailureFactory,
            ImplementationGenerationObserverFactory implementationGenerationObserverFactory,
            int maxFileGenerationAttempts
    ) {
        this.patchFailureRouter = patchFailureRouter;
        this.patchBudgetPolicy = patchBudgetPolicy;
        this.editUnitPlanner = editUnitPlanner;
        this.patchContextBuilder = patchContextBuilder;
        this.maxFileGenerationAttempts = maxFileGenerationAttempts;
        this.executionSupport = new PatchExecutionSupport(
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory
        );
        this.unitExecutor = new CodePatchUnitExecutor(
                llmProvider,
                generationEngine,
                generatedPayloadSupport,
                codeEditAdapter,
                patchFailureRouter,
                patchBudgetPolicy,
                patchPayloadRepairSupport,
                syntaxRepairSupport,
                fileGenerationFailureFactory,
                implementationGenerationObserverFactory,
                maxFileGenerationAttempts
        );
        this.scaffoldExpansionPlanner = new CodeScaffoldExpansionPlanner(editUnitPlanner);
    }

    String generate(CodePatchRequest request) {
        FilePatchProgressState patchProgressState = request.patchProgressState();
        PatchPlan patchPlan = patchUnitSizer().resize(
                editUnitPlanner.planCodePatch(request.relativePath(), request.existingContent()),
                GenerationBudgetProfile.preciseCodeUnitOutputRatio(),
                request.deliveryMode() == DeliveryMode.PATCH && request.substantiveFeedback()
        );
        LinkedList<EditUnit> pendingUnits = initialPendingUnits(request.relativePath(), patchPlan, patchProgressState);
        String currentContent = initialContent(request.existingContent(), patchProgressState);
        while (!pendingUnits.isEmpty()) {
            EditUnit unit = pendingUnits.removeFirst();
            boolean scaffoldBootstrapUnit = scaffoldExpansionPlanner.isBootstrapUnit(unit, currentContent);
            try {
                currentContent = unitExecutor.execute(request, currentContent, unit, patchContextBuilder);
                if (scaffoldBootstrapUnit && pendingUnits.isEmpty()) {
                    List<EditUnit> followUpUnits = scaffoldExpansionPlanner.planFollowUpCodeUnitsAfterScaffold(
                            request.relativePath(),
                            currentContent
                    );
                    if (!followUpUnits.isEmpty()) {
                        executionSupport.appendImplementationEvent(
                                request.eventJournal(),
                                ImplementationEventMessages.expandScaffold(
                                        request.relativePath(),
                                        FileEditStrategyNames.PRECISE_CODE,
                                        unit.label(),
                                        scaffoldExpansionPlanner.renderUnitLabels(followUpUnits)
                                )
                        );
                        pendingUnits.addAll(followUpUnits);
                    }
                }
            } catch (GenerationFailureException exception) {
                PatchFailure patchFailure = PatchFailure.of(
                        exception.report().failureType(),
                        exception.report().evidence()
                );
                List<EditUnit> splitUnits = patchFailureRouter.splitIfNeeded(unit, patchFailure);
                if (splitUnits.isEmpty()) {
                    throw exception.withPatchProgressState(new FilePatchProgressState(
                            request.relativePath(),
                            FileEditStrategyNames.PRECISE_CODE,
                            currentContent,
                            remainingUnits(unit, pendingUnits)
                    ));
                }
                executionSupport.appendImplementationEvent(
                        request.eventJournal(),
                        ImplementationEventMessages.splitUnit(
                                request.relativePath(),
                                FileEditStrategyNames.PRECISE_CODE,
                                unit.label(),
                                exception.report().failureType()
                        )
                );
                pendingUnits.addAll(0, expandRestrictedParentUnits(splitUnits));
            }
        }
        return currentContent;
    }

    private LinkedList<EditUnit> initialPendingUnits(
            Path relativePath,
            PatchPlan patchPlan,
            FilePatchProgressState patchProgressState
    ) {
        if (patchProgressState != null
                && patchProgressState.matches(relativePath, FileEditStrategyNames.PRECISE_CODE)
                && patchProgressState.resumable()) {
            return new LinkedList<>(expandRestrictedParentUnits(patchProgressState.pendingUnits()));
        }
        return new LinkedList<>(expandRestrictedParentUnits(patchPlan.units()));
    }

    private String initialContent(String existingContent, FilePatchProgressState patchProgressState) {
        if (patchProgressState == null || patchProgressState.workingContent() == null || patchProgressState.workingContent().isBlank()) {
            return existingContent;
        }
        return patchProgressState.workingContent();
    }

    private List<EditUnit> remainingUnits(EditUnit failedUnit, LinkedList<EditUnit> pendingUnits) {
        LinkedList<EditUnit> remaining = new LinkedList<>();
        remaining.add(failedUnit);
        remaining.addAll(pendingUnits);
        return List.copyOf(remaining);
    }

    private List<EditUnit> expandRestrictedParentUnits(List<EditUnit> units) {
        if (units == null || units.isEmpty()) {
            return List.of();
        }
        LinkedList<EditUnit> queue = new LinkedList<>(units);
        List<EditUnit> expanded = new LinkedList<>();
        while (!queue.isEmpty()) {
            EditUnit candidate = queue.removeFirst();
            if (candidate != null && candidate.restrictsSymbols() && candidate.splittable()) {
                List<EditUnit> splitUnits = patchFailureRouter.splitIfNeeded(
                        candidate,
                        PatchFailure.of(
                                GenerationFailureType.OUTPUT_TRUNCATED,
                                "Restricted multi-symbol precise-code units must be reduced before execution"
                        )
                );
                if (!splitUnits.isEmpty()) {
                    queue.addAll(0, splitUnits);
                    continue;
                }
            }
            if (candidate != null) {
                expanded.add(candidate);
            }
        }
        return List.copyOf(expanded);
    }

    private PatchUnitSizer patchUnitSizer() {
        return new PatchUnitSizer(patchBudgetPolicy, patchFailureRouter, PatchBudgetSettings.defaults());
    }
}
