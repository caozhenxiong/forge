package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractView;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Claude-style implementation tool loop。
 *
 * <p>这层替代旧逐文件生成主链，
 * 把一个 subtask 的实现收敛成：
 * assistant -> tool_use -> tool_result -> assistant。
 */
final class ImplementationToolLoopExecutor {

    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper;
    private final ImplementationToolPromptBuilder promptBuilder = new ImplementationToolPromptBuilder();
    private final ImplementationToolResultBudgetManager resultBudgetManager = new ImplementationToolResultBudgetManager();
    private final List<ImplementationTool> tools;
    private final int maxToolTurns;

    ImplementationToolLoopExecutor(
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            int maxToolTurns
    ) {
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
        this.maxToolTurns = Math.max(1, maxToolTurns);
        this.tools = List.of(
                new FileReadTool(),
                new FileEditTool(),
                new FileWriteTool(),
                new FileDeleteTool(),
                new GlobTool(),
                new GrepTool(),
                new BashTool()
        );
    }

    ImplementationToolLoopResult execute(
            Path projectPath,
            RunRecord runRecord,
            Subtask subtask,
            TaskPackage taskPackage,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            String feedback,
            String coderContextMarkdown,
            ImplementationEventJournal eventJournal,
            SubtaskExecutionState executionState
    ) {
        Set<Path> ownedPaths = collectOwnedPaths(subtask);
        ChatCapableLlmProvider chatCapableLlmProvider = requireChatProvider();
        ToolLoopRuntimeState runtimeState = executionState == null
                ? new ToolLoopRuntimeState()
                : executionState.toolLoopRuntimeState();
        ImplementationToolContext toolContext = new ImplementationToolContext(
                projectPath,
                runRecord,
                objectMapper,
                contractView,
                qualityPlan,
                fingerprint,
                eventJournal,
                ownedPaths,
                runtimeState
        );
        initializeTranscript(runtimeState, projectPath, subtask, taskPackage, contractView, qualityPlan, feedback, coderContextMarkdown);
        List<LlmToolDefinition> toolDefinitions = tools.stream().map(ImplementationTool::toDefinition).toList();

        for (int turn = 1; turn <= maxToolTurns; turn++) {
            toolContext.appendEvent("实现阶段｜tool-loop｜轮次开始｜子任务=%s｜轮次=%d/%d"
                    .formatted(subtask.title(), turn, maxToolTurns));
            LlmChatResponse response;
            try {
                response = chatCapableLlmProvider.chat(new LlmChatRequest(
                        runtimeState.transcript(),
                        toolDefinitions,
                        LlmOptions.withOutputBudgetRatio(Map.of(), GenerationBudgetProfile.fullBudgetRatio()),
                        ModelRole.IMPLEMENTATION
                ));
            } catch (Exception exception) {
                throw GenerationFailureExceptions.create(
                        subtask.title(),
                        subtask.deliveryMode().name(),
                        "tool-loop",
                        GenerationFailureType.MODEL_INVOCATION_FAILED,
                        turn,
                        true,
                        "tool loop 调用模型失败。",
                        exception.getMessage(),
                        "请保留当前子任务状态，继续在当前子任务上修复。"
                );
            }
            if (response.toolCalls() == null || response.toolCalls().isEmpty()) {
                if (!response.content().isBlank()) {
                    runtimeState.appendTranscript(LlmChatMessage.assistant(response.content()));
                }
                toolContext.appendEvent("实现阶段｜tool-loop｜轮次完成｜子任务=%s｜轮次=%d/%d｜触发工具=0"
                        .formatted(subtask.title(), turn, maxToolTurns));
                return new ImplementationToolLoopResult(
                        response.content(),
                        List.copyOf(toolContext.touchedPaths()),
                        toolContext.mutationRecords()
                );
            }
            runtimeState.appendTranscript(LlmChatMessage.assistantToolCalls(response.content(), response.toolCalls()));
            List<ImplementationToolResultMessage> rawResults = executeToolCalls(response.toolCalls(), toolContext);
            runtimeState.resetTranscript(resultBudgetManager.appendToolResults(
                    runtimeState.transcript(),
                    toolContext.toolResultsDirectory(),
                    rawResults,
                    runtimeState.resultReplacementState()
            ));
            toolContext.appendEvent("实现阶段｜tool-loop｜轮次完成｜子任务=%s｜轮次=%d/%d｜触发工具=%d"
                    .formatted(subtask.title(), turn, maxToolTurns, rawResults.size()));
        }
        throw GenerationFailureExceptions.create(
                subtask.title(),
                subtask.deliveryMode().name(),
                "tool-loop",
                GenerationFailureType.VALIDATION_FAILED,
                maxToolTurns,
                true,
                "子任务在 tool loop 内未能收敛。",
                "tool loop exceeded max turns without a terminal assistant response",
                "请根据当前工具错误和验收缺口继续修复，不要重新开始整个子任务。"
        );
    }

    private void initializeTranscript(
            ToolLoopRuntimeState runtimeState,
            Path projectPath,
            Subtask subtask,
            TaskPackage taskPackage,
            ContractView contractView,
            QualityPlan qualityPlan,
            String feedback,
            String coderContextMarkdown
    ) {
        if (runtimeState == null) {
            return;
        }
        if (runtimeState.transcript().isEmpty()) {
            runtimeState.resetTranscript(List.of(
                    LlmChatMessage.system(promptBuilder.systemPrompt()),
                    LlmChatMessage.user(promptBuilder.userPrompt(
                            projectPath,
                            subtask,
                            taskPackage,
                            contractView,
                            qualityPlan,
                            feedback,
                            coderContextMarkdown
                    ))
            ));
            return;
        }
        if (feedback != null && !feedback.isBlank()) {
            runtimeState.appendTranscript(LlmChatMessage.user("""
                    继续当前子任务，不要重新开始整个实现。
                    保留已有 tool loop 状态，只修复当前反馈指出的问题。

                    最新反馈：
                    %s
                    """.formatted(feedback.trim()).trim()));
        }
    }

    private ChatCapableLlmProvider requireChatProvider() {
        if (llmProvider instanceof ChatCapableLlmProvider chatCapableLlmProvider) {
            return chatCapableLlmProvider;
        }
        throw new IllegalStateException("Implementation tool loop requires a chat-capable provider.");
    }

    private List<ImplementationToolResultMessage> executeToolCalls(
            List<LlmToolCall> toolCalls,
            ImplementationToolContext context
    ) {
        List<List<LlmToolCall>> batches = partitionToolCalls(toolCalls);
        List<ImplementationToolResultMessage> results = new ArrayList<>();
        for (List<LlmToolCall> batch : batches) {
            boolean concurrent = batch.size() > 1 && tool(batch.get(0).name()).concurrencySafe();
            if (concurrent) {
                List<CompletableFuture<ImplementationToolResultMessage>> futures = batch.stream()
                        .map(call -> CompletableFuture.supplyAsync(() -> executeToolCall(call, context)))
                        .toList();
                for (CompletableFuture<ImplementationToolResultMessage> future : futures) {
                    results.add(future.join());
                }
            } else {
                for (LlmToolCall toolCall : batch) {
                    results.add(executeToolCall(toolCall, context));
                }
            }
        }
        return List.copyOf(results);
    }

    private List<List<LlmToolCall>> partitionToolCalls(List<LlmToolCall> toolCalls) {
        List<List<LlmToolCall>> batches = new ArrayList<>();
        for (LlmToolCall toolCall : toolCalls) {
            ImplementationTool tool = tool(toolCall.name());
            boolean concurrencySafe = tool != null && tool.concurrencySafe();
            if (concurrencySafe && !batches.isEmpty()) {
                List<LlmToolCall> lastBatch = batches.get(batches.size() - 1);
                ImplementationTool firstTool = tool(lastBatch.get(0).name());
                if (firstTool != null && firstTool.concurrencySafe()) {
                    lastBatch.add(toolCall);
                    continue;
                }
            }
            List<LlmToolCall> batch = new ArrayList<>();
            batch.add(toolCall);
            batches.add(batch);
        }
        return batches;
    }

    private ImplementationToolResultMessage executeToolCall(LlmToolCall toolCall, ImplementationToolContext context) {
        ImplementationTool tool = tool(toolCall.name());
        if (tool == null) {
            return new ImplementationToolResultMessage(
                    toolCall.id(),
                    toolCall.name(),
                    renderFailure(objectMapper, "Unknown tool: " + toolCall.name()),
                    8_000
            );
        }
        context.appendEvent("实现阶段｜tool-call｜开始｜工具=%s｜参数=%s".formatted(tool.name(), toolCall.arguments()));
        ToolInvocationResult result;
        try {
            result = tool.invoke(toolCall, context);
        } catch (Exception exception) {
            result = ToolInvocationResult.failure(Map.of(
                    "type", "error",
                    "message", exception.getMessage() == null ? "Tool execution failed." : exception.getMessage()
            ));
        }
        String rendered = result.render(objectMapper);
        context.appendEvent("实现阶段｜tool-call｜完成｜工具=%s｜成功=%s".formatted(tool.name(), result.success()));
        return new ImplementationToolResultMessage(
                toolCall.id(),
                tool.name(),
                rendered,
                tool.maxResultSizeChars()
        );
    }

    private String renderFailure(ObjectMapper objectMapper, String message) {
        return ToolInvocationResult.failure(Map.of("type", "error", "message", message)).render(objectMapper);
    }

    private ImplementationTool tool(String name) {
        for (ImplementationTool tool : tools) {
            if (tool.name().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    private Set<Path> collectOwnedPaths(Subtask subtask) {
        if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
            return Set.of();
        }
        Set<Path> paths = new LinkedHashSet<>();
        for (FileChange change : subtask.changes()) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            paths.add(Path.of(change.path()).normalize());
        }
        return Set.copyOf(paths);
    }
}
