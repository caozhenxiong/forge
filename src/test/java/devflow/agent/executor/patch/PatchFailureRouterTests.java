package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import devflow.agent.executor.generation.GenerationFailureType;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchFailureRouterTests {

    @Test
    void structuredToolFailureCodeEscalatesWithoutSplit() {
        PatchFailureRouter router = new PatchFailureRouter();
        EditUnit unit = new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "game.js#code-unit-1", List.of("tick", "render"));
        PatchFailure patchFailure = PatchFailure.fromToolResult(
                ToolResult.failure(
                        ToolName.PATCH_APPLY,
                        ToolFailureCode.TARGET_SCOPE_VIOLATION,
                        "target escaped current unit",
                        "请只保留当前单元允许的符号。"
                ),
                GenerationFailureType.VALIDATION_FAILED
        );

        assertEquals(GenerationFailureType.TARGET_SCOPE_VIOLATION, patchFailure.failureType());
        assertEquals(PatchFailureDisposition.ESCALATE, router.dispositionFor(unit, patchFailure));
        assertTrue(!router.shouldAbortCurrentUnit(unit, patchFailure));
    }

    @Test
    void splittableUnitIsSplitOnTruncation() {
        PatchFailureRouter router = new PatchFailureRouter();
        EditUnit unit = new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "game.js#code-unit-1", List.of("tick", "render"));
        PatchFailure patchFailure = PatchFailure.of(GenerationFailureType.OUTPUT_TRUNCATED, "too wide");

        assertEquals(PatchFailureDisposition.SPLIT_UNIT, router.dispositionFor(unit, patchFailure));
        assertTrue(router.shouldAbortCurrentUnit(unit, patchFailure));
        assertEquals(2, router.splitIfNeeded(unit, patchFailure).size());
    }

    @Test
    void unsplittableUnitKeepsSingleStructuredRetryBudget() {
        PatchFailureRouter router = new PatchFailureRouter(new PatchFailureRoutingSettings(2));
        EditUnit unit = new EditUnit(EditUnitKind.INLINE_SCRIPT_SYMBOL_BATCH, "index.html#inline-unit-1", List.of("bootstrap"));
        PatchFailure patchFailure = PatchFailure.of(GenerationFailureType.OUTPUT_TRUNCATED, "still too wide");

        assertEquals(PatchFailureDisposition.RETRY_CURRENT_UNIT, router.dispositionFor(unit, patchFailure));
        assertTrue(router.shouldRetryCurrentUnit(unit, patchFailure));
        assertEquals(2, router.attemptsFor(unit, 3), "不可再拆分的最小 patch 单元应只保留一次结构化重试空间");
    }

    @Test
    void customRoutingSettingsCanFurtherClampUnsplittableAttempts() {
        PatchFailureRouter router = new PatchFailureRouter(new PatchFailureRoutingSettings(1));
        EditUnit unit = new EditUnit(EditUnitKind.INLINE_SCRIPT_SYMBOL_BATCH, "index.html#inline-unit-1", List.of("bootstrap"));

        assertEquals(1, router.attemptsFor(unit, 3));
    }

    @Test
    void unsplittableScopeViolationEscalatesInsteadOfRetryingCurrentUnit() {
        PatchFailureRouter router = new PatchFailureRouter(new PatchFailureRoutingSettings(2));
        EditUnit unit = new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "game.js#code-unit-16", List.of("render"));
        PatchFailure patchFailure = PatchFailure.of(GenerationFailureType.TARGET_SCOPE_VIOLATION, "out of scope");

        assertEquals(PatchFailureDisposition.ESCALATE, router.dispositionFor(unit, patchFailure));
        assertTrue(!router.shouldRetryCurrentUnit(unit, patchFailure));
    }
}
