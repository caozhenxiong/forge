package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;
import devflow.agent.executor.llm.StructuredPayloadReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.editing.precise.CodePreciseEditor;
import devflow.agent.editing.precise.FileStateLedger;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedPatchUnitExecutorTests {

    @TempDir
    Path tempDir;

    @Test
    void inlineScriptUsesSyntaxRepairBeforeRetryingGeneration() {
        AtomicInteger inlineScriptCalls = new AtomicInteger();
        AtomicInteger syntaxRepairCalls = new AtomicInteger();
        String currentScript = """
                function bootstrap() {
                  const canvas = document.getElementById('game-canvas');
                  return canvas;
                }

                document.addEventListener('DOMContentLoaded', () => {
                  bootstrap();
                });
                """;
        String expectedHash = new FileStateLedger()
                .capture(ProjectPathSupport.inlineScriptSyntheticPath(Path.of("index.html")), currentScript)
                .contentHash();
        String syntheticPath = ProjectPathSupport.inlineScriptSyntheticPath(Path.of("index.html")).toString();
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("语法修复器")) {
                    syntaxRepairCalls.incrementAndGet();
                    return """
                            function bootstrap() {
                              const canvas = document.getElementById('game-canvas');
                              return canvas;
                            }

                            document.addEventListener('DOMContentLoaded', () => {
                              bootstrap();
                            });
                            """;
                }
                if (systemPrompt.contains("当前 HTML 入口文件里的主脚本已被抽成独立代码工作集")) {
                    inlineScriptCalls.incrementAndGet();
                            return """
                                    {
                                      "targetPath": "%s",
                                      "baseContentHash": "%s",
                                      "oldText": "  const canvas = document.getElementById('game-canvas');",
                                      "newText": "  const = document.getElementById('game-canvas');",
                                      "replaceAll": false
                                    }
                                    """.formatted(syntheticPath, expectedHash);
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

        devflow.agent.parsing.TreeSitterSupport treeSitterSupport = new devflow.agent.parsing.TreeSitterSupport();
        GeneratedContentGate generatedContentGate = new GeneratedContentGate(new FileProjectWorkspace(), treeSitterSupport);
        StructuredPayloadReader payloadReader = new StructuredPayloadReader(new ObjectMapper());
        GeneratedPayloadSupport generatedPayloadSupport = new GeneratedPayloadSupport(payloadReader);
        FileGenerationFailureFactory fileGenerationFailureFactory = new FileGenerationFailureFactory();
        ImplementationGenerationObserverFactory observerFactory = new ImplementationGenerationObserverFactory();
        PatchExecutionSupport patchExecutionSupport = new PatchExecutionSupport(fileGenerationFailureFactory, observerFactory);
        PatchRepairSettings patchRepairSettings = new PatchRepairSettings();
        PatchRepairClassifier repairClassifier = new PatchRepairClassifier();
        PatchPayloadRepairSupport patchPayloadRepairSupport = new PatchPayloadRepairSupport(
                generatedPayloadSupport,
                new DeterministicJsonPayloadRepairer(),
                new ModelJsonRepairTurn(provider, patchRepairSettings),
                patchRepairSettings,
                repairClassifier,
                patchExecutionSupport
        );
        PatchVerifier patchVerifier = new PatchVerifier(treeSitterSupport, generatedContentGate);
        CodePatchKernel codePatchKernel = new CodePatchKernel(patchVerifier);
        TargetLocator targetLocator = new TreeSitterTargetLocator(treeSitterSupport);
        PatchContextBuilder patchContextBuilder = new PatchContextBuilder(
                targetLocator,
                new TreeSitterCodeEditAdapter(targetLocator, new CodePreciseEditor(treeSitterSupport), codePatchKernel)
        );
        EmbeddedPatchUnitExecutor executor = new EmbeddedPatchUnitExecutor(
                provider,
                new GenerationEngine(),
                generatedPayloadSupport,
                codePatchKernel,
                new PatchFailureRouter(),
                new PatchBudgetPolicy(PatchBudgetSettings.defaults()),
                patchPayloadRepairSupport,
                new SyntaxRepairSupport(
                        repairClassifier,
                        new DeterministicSyntaxRepairer(),
                        new SyntaxRepairTurn(provider, patchRepairSettings),
                        patchRepairSettings,
                        patchVerifier,
                        new RepairDiffScopeValidator(),
                        patchExecutionSupport
                ),
                patchContextBuilder,
                fileGenerationFailureFactory,
                observerFactory,
                1
        );

        String generated = executor.execute(
                new EmbeddedTargetedRewriteRequest(
                        tempDir,
                        Path.of("index.html"),
                        "",
                        "",
                        "",
                        "",
                        "",
                        "<html></html>",
                        "",
                        DeliveryMode.PATCH,
                        null,
                        null,
                        null
                ),
                EmbeddedPatchKind.SCRIPT,
                currentScript,
                new EditUnit(EditUnitKind.INLINE_SCRIPT_SYMBOL_BATCH, "inline-unit-1", List.of("bootstrap"))
        );

        assertEquals(1, inlineScriptCalls.get());
        assertEquals(1, syntaxRepairCalls.get());
        assertTrue(generated.contains("return canvas;"));
    }
}
