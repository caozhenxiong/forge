package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.editing.StructuredDiffPatch;
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
                  "expectedSourceHash": "abc123
                ",
                  "hunks": [
                    {
                      "sourceStartLine": 2,
                      "beforeLines": ["return 0;"],
                      "afterLines": ["return 1;"]
                    },
                  ]
                }
                ```
                """);

        StructuredDiffPatch patch = reader.readJsonObject(repaired, StructuredDiffPatch.class);
        assertTrue(patch.hasAnyHunk());
        assertEquals("abc123", patch.expectedSourceHash());
        assertEquals(2, patch.hunks().getFirst().sourceStartLine());
    }
}
