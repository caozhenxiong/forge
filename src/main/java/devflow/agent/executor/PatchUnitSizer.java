package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 在真正发送模型请求前，对 patch 单元做一次预算驱动的预裁剪。
 *
 * <p>这层的目标是把“明知道预算偏宽的单元”提前拆小，
 * 而不是先打一枪再等 `OUTPUT_TRUNCATED` 回来。
 */
final class PatchUnitSizer {

    private final PatchBudgetPolicy patchBudgetPolicy;
    private final PatchFailureRouter patchFailureRouter;
    private final PatchBudgetSettings settings;

    PatchUnitSizer(
            PatchBudgetPolicy patchBudgetPolicy,
            PatchFailureRouter patchFailureRouter
    ) {
        this(patchBudgetPolicy, patchFailureRouter, PatchBudgetSettings.defaults());
    }

    PatchUnitSizer(
            PatchBudgetPolicy patchBudgetPolicy,
            PatchFailureRouter patchFailureRouter,
            PatchBudgetSettings settings
    ) {
        this.patchBudgetPolicy = patchBudgetPolicy;
        this.patchFailureRouter = patchFailureRouter;
        this.settings = settings;
    }

    PatchPlan resize(PatchPlan patchPlan) {
        return resize(patchPlan, 1.0d, false);
    }

    PatchPlan resize(
            PatchPlan patchPlan,
            double defaultOutputBudgetRatio,
            boolean aggressiveReducedUnitSplit
    ) {
        if (patchPlan == null || patchPlan.units().isEmpty()) {
            return patchPlan;
        }
        List<EditUnit> resized = new ArrayList<>();
        for (EditUnit unit : patchPlan.units()) {
            resized.addAll(resizeUnit(unit, defaultOutputBudgetRatio, aggressiveReducedUnitSplit));
        }
        return new PatchPlan(patchPlan.targetPath(), patchPlan.strategyName(), resized);
    }

    private List<EditUnit> resizeUnit(
            EditUnit unit,
            double defaultOutputBudgetRatio,
            boolean aggressiveReducedUnitSplit
    ) {
        if (unit == null) {
            return List.of();
        }
        LinkedList<EditUnit> queue = new LinkedList<>();
        List<EditUnit> resized = new ArrayList<>();
        queue.add(unit);
        while (!queue.isEmpty()) {
            EditUnit candidate = queue.removeFirst();
            double predictedOutputRatio = patchBudgetPolicy.outputBudgetRatioForUnit(candidate, defaultOutputBudgetRatio);
            if (shouldSplitBeforeExecution(candidate, aggressiveReducedUnitSplit, defaultOutputBudgetRatio, predictedOutputRatio)) {
                List<EditUnit> splitUnits = splitCandidate(candidate);
                if (splitUnits.isEmpty()) {
                    resized.add(candidate);
                    continue;
                }
                queue.addAll(0, splitUnits);
                continue;
            }
            if (predictedOutputRatio <= settings.maxPreFlightUnitRatio() || !candidate.splittable()) {
                resized.add(candidate);
                continue;
            }
            List<EditUnit> splitUnits = splitCandidate(candidate);
            if (splitUnits.isEmpty()) {
                resized.add(candidate);
                continue;
            }
            queue.addAll(0, splitUnits);
        }
        return resized;
    }

    private boolean shouldSplitBeforeExecution(
            EditUnit candidate,
            boolean aggressiveReducedUnitSplit,
            double defaultOutputBudgetRatio,
            double predictedOutputRatio
    ) {
        if (candidate == null || !candidate.splittable()) {
            return false;
        }
        if (!aggressiveReducedUnitSplit) {
            return false;
        }
        if (!candidate.restrictsSymbols()) {
            return false;
        }
        if (!candidate.reducedUnit()) {
            return false;
        }
        return predictedOutputRatio + 1e-9 < defaultOutputBudgetRatio;
    }

    private List<EditUnit> splitCandidate(EditUnit candidate) {
        return patchFailureRouter.splitIfNeeded(
                candidate,
                PatchFailure.of(GenerationFailureType.OUTPUT_TRUNCATED, "Patch unit exceeds current output budget")
        );
    }
}
