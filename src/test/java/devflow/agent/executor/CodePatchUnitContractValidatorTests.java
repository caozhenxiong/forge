package devflow.agent.executor;

import devflow.agent.editing.CodePreciseAction;
import devflow.agent.editing.CodePreciseOperation;
import devflow.agent.editing.CodePrecisePatch;
import devflow.agent.editing.PreciseEditException;
import devflow.agent.editing.PreciseEditFailureReason;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodePatchUnitContractValidatorTests {

    private final CodePatchUnitContractValidator validator = new CodePatchUnitContractValidator();

    @Test
    void strictSingleSymbolUnitRejectsWholeDeclarationPayload() {
        EditUnit unit = new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "game.js#code-unit-2-a", List.of("getRandomPiece"));
        CodePrecisePatch patch = new CodePrecisePatch(List.of(
                new CodePreciseOperation(
                        CodePreciseAction.REPLACE_SYMBOL_BODY,
                        "getRandomPiece",
                        "function",
                        null,
                        List.of(
                                "function getRandomPiece() {",
                                "  return {};",
                                "}"
                        )
                )
        ));

        PreciseEditException exception = assertThrows(
                PreciseEditException.class,
                () -> validator.validate(Path.of("index.app.js"), unit, patch)
        );

        assertEquals(PreciseEditFailureReason.PATCH_SCHEMA_INVALID, exception.reason());
    }

    @Test
    void strictSingleSymbolUnitAcceptsBodyOnlyPayload() {
        EditUnit unit = new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "game.js#code-unit-2-a", List.of("getRandomPiece"));
        CodePrecisePatch patch = new CodePrecisePatch(List.of(
                new CodePreciseOperation(
                        CodePreciseAction.REPLACE_SYMBOL_BODY,
                        "getRandomPiece",
                        "function",
                        null,
                        List.of(
                                "const randomIndex = Math.floor(Math.random() * PIECES.length);",
                                "return { ...PIECES[randomIndex] };"
                        )
                )
        ));

        assertDoesNotThrow(() -> validator.validate(Path.of("index.app.js"), unit, patch));
    }

    @Test
    void strictSingleSymbolUnitRepairsWholeFunctionDeclarationIntoBodyOnlyPayload() {
        EditUnit unit = new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "game.js#code-unit-2-a", List.of("getRandomPiece"));
        CodePrecisePatch patch = new CodePrecisePatch(List.of(
                new CodePreciseOperation(
                        CodePreciseAction.REPLACE_SYMBOL_BODY,
                        "getRandomPiece",
                        "function",
                        null,
                        List.of(
                                "function getRandomPiece() {",
                                "  const randomIndex = Math.floor(Math.random() * PIECES.length);",
                                "  return { ...PIECES[randomIndex] };",
                                "}"
                        )
                )
        ));

        CodePrecisePatch repaired = validator.repairRepeatedDeclarationPayload(unit, patch);

        assertNotEquals(patch, repaired);
        assertEquals(
                List.of(
                        "  const randomIndex = Math.floor(Math.random() * PIECES.length);",
                        "  return { ...PIECES[randomIndex] };"
                ),
                repaired.operations().getFirst().contentLines()
        );
        assertDoesNotThrow(() -> validator.validate(Path.of("index.app.js"), unit, repaired));
    }

    @Test
    void strictSingleSymbolUnitRepairsRepeatedNestedDeclarationWrappers() {
        EditUnit unit = new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "game.js#code-unit-2-a", List.of("renderGrid"));
        CodePrecisePatch patch = new CodePrecisePatch(List.of(
                new CodePreciseOperation(
                        CodePreciseAction.REPLACE_SYMBOL_BODY,
                        "renderGrid",
                        "function",
                        null,
                        List.of(
                                "function renderGrid() {",
                                "  function renderGrid() {",
                                "    drawBoard();",
                                "  }",
                                "}"
                        )
                )
        ));

        CodePrecisePatch repaired = validator.repairRepeatedDeclarationPayload(unit, patch);

        assertNotEquals(patch, repaired);
        assertEquals(
                List.of("    drawBoard();"),
                repaired.operations().getFirst().contentLines()
        );
        assertDoesNotThrow(() -> validator.validate(Path.of("index.app.js"), unit, repaired));
    }
}
