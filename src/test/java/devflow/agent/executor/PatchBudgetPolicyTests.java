package devflow.agent.executor;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatchBudgetPolicyTests {

    @Test
    void unsplittableUnitKeepsDefaultBudget() {
        PatchBudgetPolicy policy = new PatchBudgetPolicy();
        EditUnit unit = new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "game.js#code-unit-1", List.of("tick"));

        assertEquals(
                GenerationBudgetProfile.preciseCodeUnitOutputRatio(),
                policy.outputBudgetRatioForUnit(unit, GenerationBudgetProfile.preciseCodeUnitOutputRatio())
        );
    }

    @Test
    void appendOnlyUnitScalesOutputRatioWithAppendAllowance() {
        PatchBudgetPolicy policy = new PatchBudgetPolicy();
        EditUnit unit = new EditUnit(EditUnitKind.INLINE_SCRIPT_SYMBOL_BATCH, "index.html#inline-unit-append", List.of(), 2);

        assertEquals(
                0.5d,
                policy.outputBudgetRatioForUnit(unit, GenerationBudgetProfile.inlineScriptUnitOutputRatio())
        );
    }
}
