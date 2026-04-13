package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationBudgetProfile;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatchUnitSizerTests {

    @Test
    void oversizedCodeUnitIsSplitBeforeExecution() {
        PatchBudgetSettings settings = new PatchBudgetSettings(
                0.25d,
                0.75d
        );
        PatchUnitSizer sizer = new PatchUnitSizer(
                new PatchBudgetPolicy(settings),
                new PatchFailureRouter(),
                settings
        );
        PatchPlan patchPlan = new PatchPlan(
                Path.of("game.js"),
                FileEditStrategyNames.PRECISE_CODE,
                List.of(new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "game.js#code-unit-1", List.of("a", "b", "c", "d")))
        );

        PatchPlan resized = sizer.resize(patchPlan, GenerationBudgetProfile.preciseCodeUnitOutputRatio(), false);

        assertEquals(2, resized.units().size());
        assertEquals(List.of("a", "b"), resized.units().get(0).allowedSymbols());
        assertEquals(List.of("c", "d"), resized.units().get(1).allowedSymbols());
    }

    @Test
    void minimalUnsplittableUnitIsPreserved() {
        PatchBudgetSettings settings = new PatchBudgetSettings(
                0.25d,
                0.75d
        );
        PatchUnitSizer sizer = new PatchUnitSizer(
                new PatchBudgetPolicy(settings),
                new PatchFailureRouter(),
                settings
        );
        PatchPlan patchPlan = new PatchPlan(
                Path.of("index.html.inline.js"),
                FileEditStrategyNames.INLINE_SCRIPT_WORKSET,
                List.of(new EditUnit(EditUnitKind.INLINE_SCRIPT_SYMBOL_BATCH, "index.html#inline-unit-1", List.of("bootstrap")))
        );

        PatchPlan resized = sizer.resize(patchPlan, GenerationBudgetProfile.preciseCodeUnitOutputRatio(), false);

        assertEquals(1, resized.units().size());
        assertEquals(List.of("bootstrap"), resized.units().get(0).allowedSymbols());
    }

    @Test
    void aggressivePatchRepairPreSplitsReducedMultiSymbolUnit() {
        PatchBudgetSettings settings = new PatchBudgetSettings(
                0.25d,
                0.75d
        );
        PatchUnitSizer sizer = new PatchUnitSizer(
                new PatchBudgetPolicy(settings),
                new PatchFailureRouter(),
                settings
        );
        PatchPlan patchPlan = new PatchPlan(
                Path.of("index.app.js"),
                FileEditStrategyNames.PRECISE_CODE,
                List.of(new EditUnit(
                        EditUnitKind.CODE_SYMBOL_BATCH,
                        "index.app.js#code-unit-1-a",
                        List.of("constructor", "getRandomPiece"),
                        0,
                        1
                ))
        );

        PatchPlan resized = sizer.resize(
                patchPlan,
                GenerationBudgetProfile.preciseCodeUnitOutputRatio(),
                true
        );

        assertEquals(2, resized.units().size());
        assertEquals(List.of("constructor"), resized.units().get(0).allowedSymbols());
        assertEquals(List.of("getRandomPiece"), resized.units().get(1).allowedSymbols());
    }

    @Test
    void defaultPatchSizingKeepsReducedMultiSymbolUnitForNormalFlow() {
        PatchBudgetSettings settings = new PatchBudgetSettings(
                0.25d,
                0.75d
        );
        PatchUnitSizer sizer = new PatchUnitSizer(
                new PatchBudgetPolicy(settings),
                new PatchFailureRouter(),
                settings
        );
        PatchPlan patchPlan = new PatchPlan(
                Path.of("game.js"),
                FileEditStrategyNames.PRECISE_CODE,
                List.of(new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "game.js#code-unit-1", List.of("Game", "tick")))
        );

        PatchPlan resized = sizer.resize(
                patchPlan,
                GenerationBudgetProfile.preciseCodeUnitOutputRatio(),
                false
        );

        assertEquals(1, resized.units().size());
        assertEquals(List.of("Game", "tick"), resized.units().get(0).allowedSymbols());
    }
}
