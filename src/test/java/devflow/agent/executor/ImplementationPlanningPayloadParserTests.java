package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationPlanningPayloadParserTests {

    @Test
    void localMechanicalRepairHandlesMissingTrailingBraceWithoutCallingModelRepair() {
        RecordingLlmProvider llmProvider = new RecordingLlmProvider(List.of());
        ImplementationPlanningPayloadParser parser = new ImplementationPlanningPayloadParser(
                llmProvider,
                new ObjectMapper(),
                2
        );

        ImplementationPlanningPayloadParser.ParseResult<ImplementationOutline> result = parser.parseOutline("""
                {
                  "summary": "summary",
                  "subtasks": [
                    {
                      "id": "subtask-1",
                      "title": "first",
                      "goal": "goal",
                      "deliveryMode": "INCREMENTAL",
                      "runnableMilestone": false,
                      "coverageRefs": [],
                      "ownedCapabilities": ["cap"],
                      "deferredCapabilities": [],
                      "acceptanceCriteria": ["acc"],
                      "targetPaths": ["src/a.js"]
                    }
                  ]
                """
                , null, 1);

        assertEquals("summary", result.payload().summary());
        assertEquals(0, llmProvider.generateCalls());
    }

    @Test
    void fallsBackToModelRepairForMissingJsonObject() {
        RecordingLlmProvider llmProvider = new RecordingLlmProvider(List.of("""
                {
                  "summary": "summary",
                  "subtasks": [
                    {
                      "id": "subtask-1",
                      "title": "first",
                      "goal": "goal",
                      "deliveryMode": "INCREMENTAL",
                      "runnableMilestone": false,
                      "coverageRefs": [],
                      "ownedCapabilities": ["cap"],
                      "deferredCapabilities": [],
                      "acceptanceCriteria": ["acc"],
                      "targetPaths": ["src/a.js"]
                    }
                  ]
                }
                """));
        ImplementationPlanningPayloadParser parser = new ImplementationPlanningPayloadParser(
                llmProvider,
                new ObjectMapper(),
                2
        );

        ImplementationPlanningPayloadParser.ParseResult<ImplementationOutline> result = parser.parseOutline(
                "not a json payload",
                null,
                1
        );

        assertEquals("summary", result.payload().summary());
        assertEquals(1, llmProvider.generateCalls());
        assertTrue(result.repairedResponse().contains("\"subtasks\""));
    }

    private static final class RecordingLlmProvider implements LlmProvider {
        private final java.util.Queue<String> responses;
        private int generateCalls;

        private RecordingLlmProvider(List<String> responses) {
            this.responses = new java.util.ArrayDeque<>(responses);
        }

        @Override
        public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
            generateCalls++;
            return responses.remove();
        }

        private int generateCalls() {
            return generateCalls;
        }
    }
}
