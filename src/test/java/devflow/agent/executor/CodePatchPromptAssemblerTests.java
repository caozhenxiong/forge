package devflow.agent.executor;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodePatchPromptAssemblerTests {

    @Test
    void strictSingleSymbolPromptPinsUniqueTargetSymbol() {
        EditUnit unit = new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "index.app.js#code-unit-1-a-a", List.of("GameLogic"));

        PatchGenerationPrompt prompt = CodePatchPromptAssembler.assemble(
                Path.of("index.app.js"),
                "实现核心逻辑",
                "- 子任务",
                "",
                "补齐核心逻辑",
                "",
                "- symbol: class GameLogic insertion=true",
                "- current context",
                "class GameLogic {}",
                "class GameLogic {}",
                unit,
                true,
                true
        );

        assertTrue(prompt.systemPrompt().contains("operation.targetSymbol 必须精确等于 \"GameLogic\""));
        assertTrue(prompt.userPrompt().contains("- strictTargetSymbol: GameLogic"));
    }
}
