package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.toolloop.*;

import devflow.agent.executor.llm.LlmProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void parsesMinimalSubtaskDetailSchema() {
        RecordingLlmProvider llmProvider = new RecordingLlmProvider(List.of());
        ImplementationPlanningPayloadParser parser = new ImplementationPlanningPayloadParser(
                llmProvider,
                new ObjectMapper(),
                2
        );

        ImplementationPlanningPayloadParser.ParseResult<ImplementationSubtaskDetail> result = parser.parseSubtaskDetail(
                "subtask-1",
                """
                {
                  "subtaskId": "subtask-1",
                  "changes": [
                    {
                      "path": "src/app.js",
                      "action": "WRITE",
                      "reason": "补齐入口脚本"
                    }
                  ]
                }
                """,
                null,
                1
        );

        assertEquals("subtask-1", result.payload().subtaskId());
        assertEquals(1, result.payload().changes().size());
        assertEquals("src/app.js", result.payload().changes().getFirst().path());
        assertEquals(0, llmProvider.generateCalls());
    }

    @Test
    void rejectsLegacySubtaskDetailFieldsWhenRepairCannotProduceMinimalSchema() {
        RecordingLlmProvider llmProvider = new RecordingLlmProvider(List.of("""
                {
                  "subtaskId": "subtask-1",
                  "changes": [
                    {
                      "path": "src/app.js",
                      "action": "WRITE",
                      "reason": "补齐入口脚本",
                      "editScope": "AUTO"
                    }
                  ]
                }
                """));
        ImplementationPlanningPayloadParser parser = new ImplementationPlanningPayloadParser(
                llmProvider,
                new ObjectMapper(),
                2
        );

        assertThrows(
                ImplementationPlanningException.class,
                () -> parser.parseSubtaskDetail("subtask-1", """
                        {
                          "subtaskId": "subtask-1",
                          "changes": [
                            {
                              "path": "src/app.js",
                              "action": "WRITE",
                              "reason": "补齐入口脚本",
                              "editScope": "AUTO",
                              "runtimeOwnership": null,
                              "hostHtmlPatchRequired": false
                            }
                          ]
                        }
                        """, null, 1)
        );
        assertEquals(1, llmProvider.generateCalls());
    }

    private static final class RecordingLlmProvider extends devflow.agent.testsupport.RequestBackedLlmProvider {
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
