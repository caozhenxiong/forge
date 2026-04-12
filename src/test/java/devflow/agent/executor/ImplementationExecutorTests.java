package devflow.agent.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageStatus;
import devflow.agent.orchestrator.StageType;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewSemantics;
import devflow.agent.review.StructuredReviewResult;
import devflow.agent.validation.ValidationExecutionReport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationExecutorTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void executesPlannedJavaScriptPatchThroughToolLoopOnly() throws Exception {
        Path file = tempDir.resolve("src/app.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                export function tick() {
                  return 0;
                }
                """);

        ScriptedImplementationProvider provider = new ScriptedImplementationProvider(
                List.of(
                        outlineJson(new PlannedSubtask("subtask-1", "更新 tick", "修复 tick 返回值", DeliveryMode.PATCH, false, "src/app.js")),
                        detailJson("subtask-1", "src/app.js")
                ),
                List.of(
                        ChatStep.response(toolCall("tool-1", "Read", Map.of("file_path", file.toString()))),
                        ChatStep.response(toolCall(
                                "tool-2",
                                "Edit",
                                Map.of(
                                        "file_path", file.toString(),
                                        "old_string", "return 0;",
                                        "new_string", "return 1;"
                                )
                        )),
                        ChatStep.response(finalResponse("patched"))
                ),
                approvedReview()
        );
        ImplementationExecutionBundle bundle = newExecutor(provider).execute(
                tempDir,
                runRecord("修复 JavaScript 方法", ""),
                "",
                "",
                "",
                ""
        );

        assertTrue(Files.readString(file).contains("return 1;"));
        assertEquals(1, bundle.snapshot().reports().size());
        assertTrue(bundle.snapshot().reports().get(0).completed());
        assertTrue(bundle.eventsMarkdown().contains("实现阶段｜tool-loop｜轮次开始"));

        LlmChatRequest request = provider.chatRequests().get(0);
        assertEquals(ModelRole.IMPLEMENTATION, request.role());
        assertFalse(request.tools().isEmpty());
        String userPrompt = request.messages().stream()
                .filter(message -> message.role() == LlmChatRole.USER)
                .findFirst()
                .orElseThrow()
                .content();
        assertTrue(userPrompt.contains("# Writable Files"));
        assertTrue(userPrompt.contains(file.toString()));
        assertTrue(userPrompt.contains("# Current Subtask"));
        assertFalse(userPrompt.contains("精确改写"), userPrompt);
    }

    @Test
    void preservesSkeletonTaskPackageForHtmlBootstrap() throws Exception {
        Path file = tempDir.resolve("index.html");

        ScriptedImplementationProvider provider = new ScriptedImplementationProvider(
                List.of(
                        outlineJson(new PlannedSubtask("subtask-1", "建立入口", "创建最小可运行入口", DeliveryMode.SKELETON, true, "index.html")),
                        detailJson("subtask-1", "index.html")
                ),
                List.of(
                        ChatStep.response(toolCall(
                                "tool-1",
                                "Write",
                                Map.of(
                                        "file_path", file.toString(),
                                        "content", """
                                                <!DOCTYPE html>
                                                <html lang="zh-CN">
                                                <head>
                                                  <meta charset="UTF-8">
                                                  <title>Bootstrap</title>
                                                </head>
                                                <body>
                                                  <main id="app-root">
                                                    <h1>Bootstrap</h1>
                                                  </main>
                                                </body>
                                                </html>
                                                """
                                )
                        )),
                        ChatStep.response(finalResponse("bootstrap ready"))
                ),
                approvedReview()
        );
        ImplementationExecutionBundle bundle = newExecutor(provider).execute(
                tempDir,
                runRecord("实现一个网页入口", "需要最小可运行入口"),
                "",
                "",
                "",
                ""
        );

        assertTrue(Files.exists(file));
        assertEquals(DeliveryMode.SKELETON, bundle.snapshot().plan().subtasks().get(0).deliveryMode());
        assertTrue(bundle.taskPackagesMarkdown().contains("SKELETON"));
        assertTrue(bundle.snapshot().reports().get(0).completed());
    }

    @Test
    void laterToolLoopFailureReturnsBundleInsteadOfRestartingWholePlan() throws Exception {
        Path first = tempDir.resolve("src/first.js");
        Path second = tempDir.resolve("src/second.js");
        Files.createDirectories(first.getParent());
        Files.writeString(first, """
                export function firstStep() {
                  return 0;
                }
                """);
        Files.writeString(second, """
                export function secondStep() {
                  return 0;
                }
                """);

        ScriptedImplementationProvider provider = new ScriptedImplementationProvider(
                List.of(
                        outlineJson(
                                new PlannedSubtask("subtask-1", "修复 first", "先完成 first", DeliveryMode.PATCH, false, "src/first.js"),
                                new PlannedSubtask("subtask-2", "修复 second", "再完成 second", DeliveryMode.PATCH, false, "src/second.js")
                        ),
                        detailJson("subtask-1", "src/first.js"),
                        detailJson("subtask-2", "src/second.js")
                ),
                List.of(
                        ChatStep.response(toolCall("tool-1", "Read", Map.of("file_path", first.toString()))),
                        ChatStep.response(toolCall(
                                "tool-2",
                                "Edit",
                                Map.of(
                                        "file_path", first.toString(),
                                        "old_string", "return 0;",
                                        "new_string", "return 1;"
                                )
                        )),
                        ChatStep.response(finalResponse("first done")),
                        ChatStep.failure(new RuntimeException("model invocation failed")),
                        ChatStep.failure(new RuntimeException("model invocation failed")),
                        ChatStep.failure(new RuntimeException("model invocation failed"))
                ),
                approvedReview()
        );
        ImplementationExecutionBundle bundle = newExecutor(provider).execute(
                tempDir,
                runRecord("修复两个 JavaScript 方法", ""),
                "",
                "",
                "",
                ""
        );

        assertTrue(Files.readString(first).contains("return 1;"));
        assertTrue(Files.readString(second).contains("return 0;"));
        assertEquals(2, bundle.snapshot().reports().size());
        assertTrue(bundle.snapshot().reports().get(0).completed());
        assertFalse(bundle.snapshot().reports().get(1).completed());
        assertFalse(bundle.snapshot().stageStatus().planCompleted());
        assertEquals(1, bundle.snapshot().stageStatus().completedSubtasks());
        assertTrue(bundle.snapshot().stageStatus().incompleteSubtasks().contains("修复 second"));
        SubtaskAttemptReport failedAttempt = bundle.snapshot().reports().get(1).attempts().get(0);
        assertNotNull(failedAttempt.generationFailure());
        assertTrue(failedAttempt.generationFailure().summary().contains("tool loop 调用模型失败"));
    }

    private ImplementationExecutor newExecutor(ScriptedImplementationProvider provider) {
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        ObjectMapper objectMapper = new ObjectMapper();
        return new ImplementationExecutor(
                provider,
                workspace,
                objectMapper,
                new StubTestExecutor(workspace, provider, objectMapper)
        );
    }

    private RunRecord runRecord(String goal, String constraints) {
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                goal,
                constraints,
                RunConfig.defaultConfig(),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }

    private static StructuredReviewResult approvedReview() {
        return new StructuredReviewResult(
                new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "通过", ""),
                ReviewSemantics.empty()
        );
    }

    private static LlmChatResponse toolCall(String id, String name, Map<String, Object> arguments) {
        return new LlmChatResponse("", List.of(new LlmToolCall(id, name, arguments)), null, "");
    }

    private static LlmChatResponse finalResponse(String content) {
        return new LlmChatResponse(content, List.of(), null, "stop");
    }

    private static String outlineJson(PlannedSubtask... subtasks) {
        ObjectNode root = JSON.createObjectNode();
        root.put("summary", "scripted plan");
        ArrayNode subtasksNode = root.putArray("subtasks");
        for (PlannedSubtask subtask : subtasks) {
            ObjectNode node = subtasksNode.addObject();
            node.put("id", subtask.id());
            node.put("title", subtask.title());
            node.put("goal", subtask.goal());
            node.put("deliveryMode", subtask.deliveryMode().name());
            node.put("runnableMilestone", subtask.runnableMilestone());
            node.putArray("coverageRefs");
            node.putArray("ownedCapabilities").add(subtask.goal());
            node.putArray("deferredCapabilities");
            node.putArray("acceptanceCriteria").add(subtask.title() + " 已完成");
            node.putArray("targetPaths").add(subtask.path());
        }
        return writeJson(root);
    }

    private static String detailJson(String subtaskId, String path) {
        ObjectNode root = JSON.createObjectNode();
        root.put("subtaskId", subtaskId);
        ObjectNode change = root.putArray("changes").addObject();
        change.put("path", path);
        change.put("action", ChangeAction.WRITE.name());
        change.put("reason", "完成 " + path);
        change.put("editScope", FileEditScope.AUTO.name());
        change.putNull("runtimeOwnership");
        change.put("hostHtmlPatchRequired", false);
        return writeJson(root);
    }

    private static String writeJson(ObjectNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record PlannedSubtask(
            String id,
            String title,
            String goal,
            DeliveryMode deliveryMode,
            boolean runnableMilestone,
            String path
    ) {
    }

    private record ChatStep(
            LlmChatResponse response,
            RuntimeException failure
    ) {
        private static ChatStep response(LlmChatResponse response) {
            return new ChatStep(response, null);
        }

        private static ChatStep failure(RuntimeException failure) {
            return new ChatStep(null, failure);
        }
    }

    /**
     * 这个测试桩故意拒绝旧 implementation generate 主链：
     * 只有 planner 的 outline/detail 生成允许走 `generate`，
     * 真正的实现阶段必须走 chat/tool loop。
     */
    private static final class ScriptedImplementationProvider implements LlmProvider, ChatCapableLlmProvider {

        private final Queue<String> planningResponses;
        private final Queue<ChatStep> chatSteps;
        private final StructuredReviewResult reviewResult;
        private final List<LlmChatRequest> chatRequests = new ArrayList<>();

        private ScriptedImplementationProvider(
                List<String> planningResponses,
                List<ChatStep> chatSteps,
                StructuredReviewResult reviewResult
        ) {
            this.planningResponses = new ArrayDeque<>(planningResponses);
            this.chatSteps = new ArrayDeque<>(chatSteps);
            this.reviewResult = reviewResult;
        }

        @Override
        public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
            return generate(systemPrompt, userPrompt, options, null);
        }

        @Override
        public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
            boolean planningPrompt = systemPrompt != null
                    && (systemPrompt.contains("implementation outline 规划器")
                    || systemPrompt.contains("implementation 子任务 detail 规划器"));
            if (!planningPrompt || role != ModelRole.IMPLEMENTATION) {
                throw new AssertionError("Legacy implementation generate path should not be used: " + systemPrompt);
            }
            if (planningResponses.isEmpty()) {
                throw new AssertionError("No scripted planning response left for prompt: " + systemPrompt);
            }
            return planningResponses.remove();
        }

        @Override
        public LlmChatResponse chat(LlmChatRequest request) {
            chatRequests.add(request);
            if (chatSteps.isEmpty()) {
                return finalResponse("done");
            }
            ChatStep step = chatSteps.remove();
            if (step.failure() != null) {
                throw step.failure();
            }
            return step.response();
        }

        @Override
        public StructuredReviewResult reviewStructured(
                String systemPrompt,
                String candidateContent,
                Map<String, Object> options
        ) {
            return reviewResult;
        }

        private List<LlmChatRequest> chatRequests() {
            return chatRequests;
        }
    }

    private static final class StubTestExecutor extends TestExecutor {

        private StubTestExecutor(
                FileProjectWorkspace workspace,
                LlmProvider llmProvider,
                ObjectMapper objectMapper
        ) {
            super(workspace, llmProvider, objectMapper);
        }

        @Override
        ValidationExecutionReport selfCheckDetailed(Path projectPath) {
            return new ValidationExecutionReport(
                    new SelfCheckResult(true, "ok", ""),
                    List.of()
            );
        }

        @Override
        SubtaskVerificationOutcome verifyImplementationSubtask(
                Path projectPath,
                Subtask subtask,
                devflow.agent.context.ContractView contractView,
                devflow.agent.quality.QualityPlan qualityPlan,
                devflow.agent.validation.ProjectFingerprint fingerprint,
                boolean finalSubtask,
                devflow.agent.i18n.DocumentLanguage language
        ) {
            return null;
        }
    }
}
