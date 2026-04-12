package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.editing.ExactReplaceEdit;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchPayloadRepairSupportTests {

    @Test
    void usesModelJsonRepairAfterDeterministicRepairFails() {
        AtomicBoolean modelRepairCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("JSON 载荷修复器")) {
                    modelRepairCalled.set(true);
                    return """
                            {
                              "targetPath": "game.js",
                              "baseContentHash": "abc123",
                              "oldText": "return 0;",
                              "newText": "return 1;",
                              "replaceAll": false
                            }
                            """;
                }
                return "";
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public devflow.agent.review.ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                throw new UnsupportedOperationException();
            }
        };
        GeneratedPayloadSupport generatedPayloadSupport = new GeneratedPayloadSupport(
                new StructuredPayloadReader(new ObjectMapper())
        );
        PatchPayloadRepairSupport repairSupport = new PatchPayloadRepairSupport(
                generatedPayloadSupport,
                new DeterministicJsonPayloadRepairer(),
                new ModelJsonRepairTurn(provider, new PatchRepairSettings()),
                new PatchRepairSettings(),
                new PatchRepairClassifier(),
                new PatchExecutionSupport(new FileGenerationFailureFactory(), new ImplementationGenerationObserverFactory())
        );

        ExactReplaceEdit patch = repairSupport.readStructuredPayload(
                Path.of("game.js"),
                new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "code-unit-1", java.util.List.of("tick")),
                "{ invalid json",
                ExactReplaceEdit.class,
                null
        );

        assertTrue(modelRepairCalled.get());
        assertEquals("game.js", patch.targetPath());
        assertEquals("abc123", patch.baseContentHash());
        assertEquals("return 1;", patch.newText());
    }

    @Test
    void supportsJsonRepairForNonUnitStructuredPayloads() {
        AtomicBoolean modelRepairCalled = new AtomicBoolean(false);
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("JSON 载荷修复器")) {
                    modelRepairCalled.set(true);
                    return """
                            {
                              "markupHtml": "<main id=\\"app-root\\"></main>",
                              "styleCss": null,
                              "scriptJs": null,
                              "headAppendHtml": null,
                              "bodyAppendHtml": null
                            }
                            """;
                }
                return "";
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, null);
            }

            @Override
            public devflow.agent.review.ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                throw new UnsupportedOperationException();
            }
        };
        GeneratedPayloadSupport generatedPayloadSupport = new GeneratedPayloadSupport(
                new StructuredPayloadReader(new ObjectMapper())
        );
        PatchPayloadRepairSupport repairSupport = new PatchPayloadRepairSupport(
                generatedPayloadSupport,
                new DeterministicJsonPayloadRepairer(),
                new ModelJsonRepairTurn(provider, new PatchRepairSettings()),
                new PatchRepairSettings(),
                new PatchRepairClassifier(),
                new PatchExecutionSupport(new FileGenerationFailureFactory(), new ImplementationGenerationObserverFactory())
        );

        devflow.agent.editing.HtmlPrecisePatch patch = repairSupport.readStructuredPayload(
                Path.of("index.html"),
                FileEditStrategyNames.PRECISE_HTML,
                "{ invalid json",
                devflow.agent.editing.HtmlPrecisePatch.class,
                null
        );

        assertTrue(modelRepairCalled.get());
        assertEquals("<main id=\"app-root\"></main>", patch.markupHtml());
    }
}
