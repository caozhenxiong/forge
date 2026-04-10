package devflow.agent.executor;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageType;
import java.time.Instant;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntaxRepairSupportTests {

    @TempDir
    Path tempDir;

    @Test
    void deterministicSyntaxRepairClosesMissingBlock() {
        SyntaxRepairSupport repairSupport = repairSupport(new NoOpRepairProvider());
        CodePatchRequest request = codePatchRequest();
        PatchApplyResult repaired = repairSupport.repairCodeFile(
                request,
                "",
                new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "code-unit-1", java.util.List.of("tick")),
                PatchFailure.fromToolResult(
                        ToolResult.failure(ToolName.CONTENT_VERIFY, ToolFailureCode.TREE_SITTER_PARSE_FAILED, "parse failed", ""),
                        GenerationFailureType.TREE_SITTER_PARSE_FAILED
                ),
                new PatchApplyResult(
                        """
                        function tick() {
                          if (true) {
                            return 1;
                        """,
                        ToolResult.success(ToolName.PATCH_APPLY),
                        ToolResult.failure(ToolName.CONTENT_VERIFY, ToolFailureCode.TREE_SITTER_PARSE_FAILED, "parse failed", "")
                )
        );

        assertTrue(repaired.succeeded());
        assertTrue(repaired.content().contains("return 1;"));
        assertTrue(repaired.content().stripTrailing().endsWith("}"));
    }

    @Test
    void modelSyntaxRepairRunsWhenDeterministicRepairCannotFix() {
        AtomicBoolean modelRepairCalled = new AtomicBoolean(false);
        SyntaxRepairSupport repairSupport = repairSupport(new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("语法修复器")) {
                    modelRepairCalled.set(true);
                    return """
                            function tick() {
                              return 1;
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
        });
        CodePatchRequest request = codePatchRequest();
        PatchApplyResult repaired = repairSupport.repairCodeFile(
                request,
                "",
                new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "code-unit-1", java.util.List.of("tick")),
                PatchFailure.fromToolResult(
                        ToolResult.failure(ToolName.CONTENT_VERIFY, ToolFailureCode.TREE_SITTER_PARSE_FAILED, "parse failed", ""),
                        GenerationFailureType.TREE_SITTER_PARSE_FAILED
                ),
                new PatchApplyResult(
                        "function tick( { return 1; }",
                        ToolResult.success(ToolName.PATCH_APPLY),
                        ToolResult.failure(ToolName.CONTENT_VERIFY, ToolFailureCode.TREE_SITTER_PARSE_FAILED, "parse failed", "")
                )
        );

        assertTrue(modelRepairCalled.get());
        assertTrue(repaired.succeeded());
        assertEquals(ToolName.PATCH_APPLY, repaired.applyResult().toolName());
    }

    @Test
    void deterministicStructureRepairUnwrapsDuplicatedFunctionWrapper() {
        SyntaxRepairSupport repairSupport = repairSupport(new NoOpRepairProvider());
        CodePatchRequest request = codePatchRequest();
        PatchApplyResult repaired = repairSupport.repairCodeFile(
                request,
                "",
                new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "code-unit-2-a", java.util.List.of("getRandomPiece")),
                PatchFailure.fromToolResult(
                        ToolResult.failure(
                                ToolName.CONTENT_VERIFY,
                                ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID,
                                "duplicated wrapper",
                                ""
                        ),
                        GenerationFailureType.RESULT_FILE_INVALID
                ),
                new PatchApplyResult(
                        """
                        function getRandomPiece() {
                          function getRandomPiece() {
                            return { value: 1 };
                          }
                        }
                        """,
                        ToolResult.success(ToolName.PATCH_APPLY),
                        ToolResult.failure(
                                ToolName.CONTENT_VERIFY,
                                ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID,
                                "duplicated wrapper",
                                ""
                        )
                )
        );

        assertTrue(repaired.succeeded());
        assertEquals(1, repaired.content().split("function getRandomPiece").length - 1);
        assertTrue(repaired.content().contains("return { value: 1 };"));
    }

    @Test
    void deterministicStructureRepairUnwrapsMultipleDuplicatedWrappersInOnePass() {
        SyntaxRepairSupport repairSupport = repairSupport(new NoOpRepairProvider());
        CodePatchRequest request = codePatchRequest();
        PatchApplyResult repaired = repairSupport.repairCodeFile(
                request,
                "",
                new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "code-unit-3-a", java.util.List.of("initGame", "startGame")),
                PatchFailure.fromToolResult(
                        ToolResult.failure(
                                ToolName.CONTENT_VERIFY,
                                ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID,
                                "duplicated wrapper",
                                ""
                        ),
                        GenerationFailureType.RESULT_FILE_INVALID
                ),
                new PatchApplyResult(
                        """
                        function initGame() {
                          function initGame() {
                            return true;
                          }
                        }

                        function startGame() {
                          function startGame() {
                            return false;
                          }
                        }
                        """,
                        ToolResult.success(ToolName.PATCH_APPLY),
                        ToolResult.failure(
                                ToolName.CONTENT_VERIFY,
                                ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID,
                                "duplicated wrapper",
                                ""
                        )
                )
        );

        assertTrue(repaired.succeeded());
        assertEquals(1, repaired.content().split("function initGame").length - 1);
        assertEquals(1, repaired.content().split("function startGame").length - 1);
    }

    @Test
    void repairDoesNotSucceedWhenModelRepairAddsOutOfScopeTopLevelSymbol() {
        AtomicBoolean modelRepairCalled = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicInteger modelRepairAttempts = new java.util.concurrent.atomic.AtomicInteger();
        System.setProperty("devflow.patch-repair.syntax-model-repair-attempts", "2");
        try {
            SyntaxRepairSupport repairSupport = repairSupport(new LlmProvider() {

                @Override
                public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                    if (systemPrompt.contains("语法修复器")) {
                        modelRepairCalled.set(true);
                        modelRepairAttempts.incrementAndGet();
                        return """
                                function tick() {
                                  return 1;
                                }

                                function leaked() {
                                  return 2;
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
            });
            CodePatchRequest request = codePatchRequest();
            PatchApplyResult repaired = repairSupport.repairCodeFile(
                    request,
                    """
                    function tick() {
                      return 0;
                    }

                    function keepMe() {
                      return 1;
                    }
                    """,
                    new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "code-unit-4-a", java.util.List.of("tick")),
                    PatchFailure.fromToolResult(
                            ToolResult.failure(
                                    ToolName.CONTENT_VERIFY,
                                    ToolFailureCode.TREE_SITTER_PARSE_FAILED,
                                    "parse failed",
                                    ""
                            ),
                            GenerationFailureType.TREE_SITTER_PARSE_FAILED
                    ),
                    new PatchApplyResult(
                            """
                            function tick( {
                              return 1;

                            function keepMe() {
                              return 1;
                            }
                            """,
                            ToolResult.success(ToolName.PATCH_APPLY),
                            ToolResult.failure(
                                    ToolName.CONTENT_VERIFY,
                                    ToolFailureCode.TREE_SITTER_PARSE_FAILED,
                                    "parse failed",
                                    ""
                            )
                    )
            );

            assertTrue(modelRepairCalled.get());
            assertTrue(repaired.failureResult() != null);
            assertEquals(ToolFailureCode.PATCH_SCOPE_VIOLATION, repaired.failureResult().failureCode());
            assertEquals(1, modelRepairAttempts.get(), "scope-failed 后不应继续在同一 repair loop 里再次模型修复");
        } finally {
            System.clearProperty("devflow.patch-repair.syntax-model-repair-attempts");
        }
    }

    @Test
    void syntaxFailureWritesCandidateContentToArtifact() {
        SyntaxRepairSupport repairSupport = repairSupport(new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("语法修复器")) {
                    return """
                            function pauseGame() {
                              if (!gameState.isRunning) return
                              gameState.isPaused = !gameState.isPaused
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
        });
        ImplementationEventJournal eventJournal = implementationEventJournal();
        CodePatchRequest request = codePatchRequest(eventJournal);
        PatchApplyResult repaired = repairSupport.repairCodeFile(
                request,
                "",
                new EditUnit(EditUnitKind.CODE_SYMBOL_BATCH, "code-unit-9", java.util.List.of("pauseGame")),
                PatchFailure.fromToolResult(
                        ToolResult.failure(ToolName.CONTENT_VERIFY, ToolFailureCode.TREE_SITTER_PARSE_FAILED, "parse failed", ""),
                        GenerationFailureType.TREE_SITTER_PARSE_FAILED
                ),
                new PatchApplyResult(
                        "function pauseGame( { gameState.isPaused = !gameState.isPaused; ",
                        ToolResult.success(ToolName.PATCH_APPLY),
                        ToolResult.failure(ToolName.CONTENT_VERIFY, ToolFailureCode.TREE_SITTER_PARSE_FAILED, "parse failed", "")
                )
        );

        assertNotNull(repaired.failureResult());
        assertEquals(ToolFailureCode.TREE_SITTER_PARSE_FAILED, repaired.failureResult().failureCode());
        Path artifactPath = tempDir.resolve(".devflow/runs/" + runId() + "/syntax_repair_failures.md");
        assertTrue(java.nio.file.Files.exists(artifactPath));
        String artifact = readString(artifactPath);
        assertTrue(artifact.contains("game.js"));
        assertTrue(artifact.contains("code-unit-9"));
        assertTrue(artifact.contains("function pauseGame()"));
    }

    private SyntaxRepairSupport repairSupport(LlmProvider provider) {
        devflow.agent.parsing.TreeSitterSupport treeSitterSupport = new devflow.agent.parsing.TreeSitterSupport();
        GeneratedContentGate contentGate = new GeneratedContentGate(new FileProjectWorkspace(), treeSitterSupport);
        return new SyntaxRepairSupport(
                new PatchRepairClassifier(),
                new DeterministicSyntaxRepairer(),
                new SyntaxRepairTurn(provider, new PatchRepairSettings()),
                new PatchRepairSettings(),
                new PatchVerifier(treeSitterSupport, contentGate),
                new RepairScopeValidator(new PatchContextBuilder(
                        new TreeSitterTargetLocator(treeSitterSupport),
                        new TreeSitterCodeEditAdapter(
                                new TreeSitterTargetLocator(treeSitterSupport),
                                new devflow.agent.editing.CodePreciseEditor(treeSitterSupport),
                                new CodePatchKernel(
                                        new devflow.agent.editing.CodePreciseEditor(treeSitterSupport),
                                        new PatchVerifier(treeSitterSupport, contentGate)
                                )
                        )
                )),
                new PatchExecutionSupport(new FileGenerationFailureFactory(), new ImplementationGenerationObserverFactory())
        );
    }

    private CodePatchRequest codePatchRequest() {
        return codePatchRequest(null);
    }

    private CodePatchRequest codePatchRequest(ImplementationEventJournal eventJournal) {
        return new CodePatchRequest(
                tempDir,
                Path.of("game.js"),
                "",
                "",
                "",
                "",
                false,
                "",
                "",
                "",
                DeliveryMode.PATCH,
                eventJournal,
                null
        );
    }

    private ImplementationEventJournal implementationEventJournal() {
        FileRunRepository runRepository = new FileRunRepository();
        return new ImplementationEventJournal(
                new EventLogStore(runRepository),
                new FileArtifactStore(runRepository),
                tempDir,
                runRecord()
        );
    }

    private UUID runId() {
        return runRecord().runId();
    }

    private RunRecord runRecord() {
        return new RunRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                tempDir,
                "goal",
                "constraints",
                null,
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                new EnumMap<>(StageType.class),
                Instant.parse("2026-04-11T00:00:00Z"),
                Instant.parse("2026-04-11T00:00:00Z")
        );
    }

    private String readString(Path path) {
        try {
            return java.nio.file.Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class NoOpRepairProvider implements LlmProvider {

        @Override
        public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
            throw new AssertionError("不应触发模型 repair");
        }

        @Override
        public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
            return generate(systemPrompt, userPrompt, options, null);
        }

        @Override
        public devflow.agent.review.ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
            throw new UnsupportedOperationException();
        }
    }
}
