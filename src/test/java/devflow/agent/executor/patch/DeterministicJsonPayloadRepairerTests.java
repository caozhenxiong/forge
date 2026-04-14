package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.StructuredPayloadReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.editing.precise.ExactReplaceEdit;
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
                  "targetPath": "game.js",
                  "baseContentHash": "abc123
                ",
                  "oldText": "return 0;",
                  "newText": "return 1;",
                  "replaceAll": false,
                }
                ```
                """);

        ExactReplaceEdit patch = reader.readJsonObject(repaired, ExactReplaceEdit.class);
        assertEquals("game.js", patch.targetPath());
        assertEquals("abc123", patch.baseContentHash());
        assertEquals("return 0;", patch.oldText());
        assertEquals("return 1;", patch.newText());
        assertTrue(!patch.replaceAll());
    }
}
