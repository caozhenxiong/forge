package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.editing.CodePrecisePatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicJsonPayloadRepairerTests {

    @Test
    void repairsTrailingCommaAndRawNewlineInString() {
        StructuredPayloadReader reader = new StructuredPayloadReader(new ObjectMapper());
        DeterministicJsonPayloadRepairer repairer = new DeterministicJsonPayloadRepairer();

        String repaired = repairer.repair("""
                ```json
                {
                  "operations": [
                    {
                      "action": "REPLACE_SYMBOL_BODY",
                      "targetSymbol": "tick",
                      "targetKind": "function",
                      "content": "return 1;
                ",
                    }
                  ],
                }
                ```
                """);

        CodePrecisePatch patch = reader.readJsonObject(repaired, CodePrecisePatch.class);
        assertTrue(patch.hasAnyOperation());
        assertEquals("tick", patch.operations().getFirst().targetSymbol());
    }
}
