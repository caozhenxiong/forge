package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;
import devflow.agent.executor.llm.StructuredPayloadReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.editing.ExactReplaceEdit;
import devflow.agent.editing.FileStateLedger;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchPayloadRepairSupportTests {

    @Test
    void usesModelJsonRepairAfterDeterministicRepairFails() {
        AtomicBoolean modelRepairCalled = new AtomicBoolean(false);
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
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
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
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

    @Test
    void repairsExactReplaceBaseHashDeterministically() {
        PatchRepairSettings settings = new PatchRepairSettings();
        PatchPayloadRepairSupport payloadRepairSupport = new PatchPayloadRepairSupport(
                new GeneratedPayloadSupport(new StructuredPayloadReader(new ObjectMapper())),
                new DeterministicJsonPayloadRepairer(),
                new ModelJsonRepairTurn(noopProvider(), settings),
                settings,
                new PatchRepairClassifier(),
                new PatchExecutionSupport(new FileGenerationFailureFactory(), new ImplementationGenerationObserverFactory())
        );
        ExactReplaceSemanticRepairSupport semanticRepairSupport = new ExactReplaceSemanticRepairSupport(
                payloadRepairSupport,
                new DeterministicExactReplaceRepairer(),
                new ExactReplaceSemanticRepairTurn(noopProvider(), settings),
                settings,
                new PatchRepairClassifier(),
                new PatchExecutionSupport(new FileGenerationFailureFactory(), new ImplementationGenerationObserverFactory())
        );
        String currentContent = "export function tick() {\n  return 0;\n}\n";
        ExactReplaceEdit repaired = semanticRepairSupport.repair(
                Path.of("src/app.js"),
                new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "code-unit-1", java.util.List.of("tick")),
                currentContent,
                """
                        {
                          "targetPath": "stale.js",
                          "baseContentHash": "stale-hash",
                          "oldText": "return 0;",
                          "newText": "return 1;",
                          "replaceAll": false
                        }
                        """,
                new ExactReplaceEdit("stale.js", "stale-hash", "return 0;", "return 1;", false),
                PatchFailure.fromToolResult(
                        ToolResult.failure(
                                ToolName.PATCH_APPLY,
                                ToolFailureCode.SNAPSHOT_STALE,
                                "stale hash",
                                "repair current unit"
                        ),
                        GenerationFailureType.VALIDATION_FAILED
                ),
                null
        );

        assertNotNull(repaired);
        assertEquals("src/app.js", repaired.targetPath());
        assertEquals(
                new FileStateLedger().capture(Path.of("src/app.js"), currentContent).contentHash(),
                repaired.baseContentHash()
        );
    }

    @Test
    void usesModelSemanticRepairForPatchEmpty() {
        AtomicBoolean semanticRepairCalled = new AtomicBoolean(false);
        String currentContent = "export function tick() {\n  return 0;\n}\n";
        String contentHash = new FileStateLedger().capture(Path.of("src/app.js"), currentContent).contentHash();
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("exact-replace 语义修复器")) {
                    semanticRepairCalled.set(true);
                    return """
                            {
                              "targetPath": "src/app.js",
                              "baseContentHash": "%s",
                              "oldText": "return 0;",
                              "newText": "return 1;",
                              "replaceAll": false
                            }
                            """.formatted(contentHash);
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
        PatchRepairSettings settings = new PatchRepairSettings();
        PatchPayloadRepairSupport payloadRepairSupport = new PatchPayloadRepairSupport(
                new GeneratedPayloadSupport(new StructuredPayloadReader(new ObjectMapper())),
                new DeterministicJsonPayloadRepairer(),
                new ModelJsonRepairTurn(provider, settings),
                settings,
                new PatchRepairClassifier(),
                new PatchExecutionSupport(new FileGenerationFailureFactory(), new ImplementationGenerationObserverFactory())
        );
        ExactReplaceSemanticRepairSupport semanticRepairSupport = new ExactReplaceSemanticRepairSupport(
                payloadRepairSupport,
                new DeterministicExactReplaceRepairer(),
                new ExactReplaceSemanticRepairTurn(provider, settings),
                settings,
                new PatchRepairClassifier(),
                new PatchExecutionSupport(new FileGenerationFailureFactory(), new ImplementationGenerationObserverFactory())
        );

        ExactReplaceEdit repaired = semanticRepairSupport.repair(
                Path.of("src/app.js"),
                new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "code-unit-3", java.util.List.of("tick")),
                currentContent,
                """
                        {
                          "targetPath": "src/app.js",
                          "baseContentHash": "%s",
                          "oldText": "return 0;",
                          "newText": "return 0;",
                          "replaceAll": false
                        }
                        """.formatted(contentHash),
                new ExactReplaceEdit("src/app.js", contentHash, "return 0;", "return 0;", false),
                PatchFailure.fromToolResult(
                        ToolResult.failure(
                                ToolName.PATCH_APPLY,
                                ToolFailureCode.MODEL_OUTPUT_INVALID,
                                "Exact replace edit oldText and newText must differ.",
                                "repair current unit"
                        ),
                        GenerationFailureType.VALIDATION_FAILED
                ),
                null
        );

        assertTrue(semanticRepairCalled.get());
        assertNotNull(repaired);
        assertEquals("return 1;", repaired.newText());
    }

    private LlmProvider noopProvider() {
        return new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                throw new UnsupportedOperationException();
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
    }
}
