package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;

import devflow.agent.executor.generation.GenerationFailureType;

import java.util.List;

/**
 * 统一维护 patch 单元失败后的硬路由。
 *
 * <p>这层的职责是把“模型失败”翻译成执行动作，而不是继续在
 * 旧文件级编辑链里散落 if/else：
 * 1. 哪些失败说明当前单元设计过宽，应立即拆小；
 * 2. 哪些失败还能在当前单元内继续一次结构化重试；
 * 3. 哪些失败已经不该再在当前单元里消耗预算，应直接升级。
 */
public final class PatchFailureRouter {

    private static final List<ToolFailureCode> RETRYABLE_UNSPLITTABLE_TOOL_FAILURES = List.of(
            ToolFailureCode.MODEL_OUTPUT_INVALID
    );

    /**
     * diff-first 主链只保留“输出截断导致单元过宽”这一类拆分理由。
     *
     * <p>其它失败都在当前单元内 repair / regenerate，不再把 scope 猜测当成拆分依据。
     */
    private static final List<GenerationFailureType> SPLIT_REQUIRED_FAILURES = List.of(
            GenerationFailureType.OUTPUT_TRUNCATED
    );

    private final PatchFailureRoutingSettings settings;

    public PatchFailureRouter() {
        this(PatchFailureRoutingSettings.defaults());
    }

    public PatchFailureRouter(PatchFailureRoutingSettings settings) {
        this.settings = settings;
    }

    public PatchFailureDisposition dispositionFor(EditUnit unit, PatchFailure patchFailure) {
        if (unit == null || patchFailure == null || patchFailure.failureType() == null) {
            return PatchFailureDisposition.ESCALATE;
        }
        if (unit.splittable() && shouldSplitCurrentUnit(patchFailure)) {
            return PatchFailureDisposition.SPLIT_UNIT;
        }
        if (shouldRetryCurrentUnit(unit, patchFailure)) {
            return PatchFailureDisposition.RETRY_CURRENT_UNIT;
        }
        return PatchFailureDisposition.ESCALATE;
    }

    public boolean shouldAbortCurrentUnit(EditUnit unit, PatchFailure patchFailure) {
        return dispositionFor(unit, patchFailure) == PatchFailureDisposition.SPLIT_UNIT;
    }

    public boolean shouldRetryCurrentUnit(EditUnit unit, PatchFailure patchFailure) {
        if (unit == null || patchFailure == null || patchFailure.failureType() == null) {
            return false;
        }
        if (unit.splittable()) {
            return false;
        }
        if (patchFailure.toolFailureCode() != null) {
            return RETRYABLE_UNSPLITTABLE_TOOL_FAILURES.contains(patchFailure.toolFailureCode());
        }
        GenerationFailureType failureType = patchFailure.failureType();
        return failureType == GenerationFailureType.OUTPUT_TRUNCATED
                || failureType == GenerationFailureType.MODEL_OUTPUT_INVALID;
    }

    private boolean shouldSplitCurrentUnit(PatchFailure patchFailure) {
        return SPLIT_REQUIRED_FAILURES.contains(patchFailure.failureType());
    }

    public List<EditUnit> splitIfNeeded(EditUnit unit, PatchFailure patchFailure) {
        if (dispositionFor(unit, patchFailure) != PatchFailureDisposition.SPLIT_UNIT) {
            return List.of();
        }
        if (unit.appendOnly() && unit.appendSymbolBudget() > 1) {
            int leftBudget = Math.max(1, unit.appendSymbolBudget() / 2);
            int rightBudget = unit.appendSymbolBudget() - leftBudget;
            if (rightBudget <= 0) {
                return List.of();
            }
            return List.of(
                    unit.splitChild(unit.label() + "-a", List.of(), leftBudget),
                    unit.splitChild(unit.label() + "-b", List.of(), rightBudget)
            );
        }
        int middle = unit.allowedSymbols().size() / 2;
        if (middle <= 0 || middle >= unit.allowedSymbols().size()) {
            return List.of();
        }
        return List.of(
                unit.splitChild(unit.label() + "-a", unit.allowedSymbols().subList(0, middle), 0),
                unit.splitChild(unit.label() + "-b", unit.allowedSymbols().subList(middle, unit.allowedSymbols().size()), 0)
        );
    }

    public int attemptsFor(EditUnit unit, int defaultAttempts) {
        if (unit == null || unit.splittable()) {
            return defaultAttempts;
        }
        return Math.min(defaultAttempts, settings.unsplittableUnitMaxAttempts());
    }
}
