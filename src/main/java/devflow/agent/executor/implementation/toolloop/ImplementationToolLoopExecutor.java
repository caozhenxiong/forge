package devflow.agent.executor.implementation.toolloop;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.FatalToolExecutionException;
import devflow.agent.executor.tools.ImplementationTool;
import devflow.agent.executor.tools.ImplementationToolPermissionContext;
import devflow.agent.executor.tools.ImplementationToolPermissionPolicy;
import devflow.agent.executor.tools.ImplementationToolRegistry;
import devflow.agent.executor.tools.ToolInvocationResult;

import devflow.agent.executor.generation.GenerationBudgetProfile;
import devflow.agent.executor.generation.GenerationFailureClassifier;
import devflow.agent.executor.generation.GenerationFailureExceptions;
import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.llm.ChatCapableLlmProvider;
import devflow.agent.executor.llm.LlmChatMessage;
import devflow.agent.executor.llm.LlmChatRequest;
import devflow.agent.executor.llm.LlmChatResponse;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.LlmToolCall;
import devflow.agent.executor.llm.LlmToolDefinition;
import devflow.agent.executor.llm.ModelRole;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.editing.FileStateSnapshot;
import devflow.agent.context.ContractView;
import devflow.agent.domain.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionState;
import devflow.agent.executor.subtask.TaskPackage;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.FileMutationRecord;
/**
 * Claude-style implementation tool loop。
 *
 * <p>这层替代旧逐文件生成主链，
 * 把一个 subtask 的实现收敛成：
 * assistant -> tool_use -> tool_result -> assistant。
 */
public final class ImplementationToolLoopExecutor {

    private static final String DONE_REASON_LENGTH = "length";
    private static final GenerationFailureClassifier GENERATION_FAILURE_CLASSIFIER = new GenerationFailureClassifier();
    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper;
    private final ImplementationToolPromptBuilder promptBuilder = new ImplementationToolPromptBuilder();
    private final ImplementationToolResultBudgetManager resultBudgetManager = new ImplementationToolResultBudgetManager();
    private final ImplementationToolRegistry toolRegistry;
    private final ImplementationToolPermissionPolicy permissionPolicy;
    private final int maxToolTurns;
    private final ExecutorService toolExecutor;

    public ImplementationToolLoopExecutor(
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            int maxToolTurns,
            ExecutorService toolExecutor
    ) {
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
        this.maxToolTurns = Math.max(1, maxToolTurns);
        this.toolRegistry = ImplementationToolRegistry.defaultRegistry();
        this.permissionPolicy = new ImplementationToolPermissionPolicy();
        this.toolExecutor = toolExecutor;
    }

    public ImplementationToolLoopResult execute(
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
        ImplementationToolSessionState toolSessionState = executionState == null
                ? new ImplementationToolSessionState()
                : executionState.toolSessionState();
        ImplementationToolPermissionContext permissionContext = permissionPolicy.build(
                projectPath,
                ownedPaths,
                toolRegistry.toolNames()
        );
        ImplementationToolContext toolContext = new ImplementationToolContext(
                projectPath,
                runRecord,
                objectMapper,
                contractView,
                qualityPlan,
                fingerprint,
                eventJournal,
                toolSessionState,
                permissionContext,
                permissionPolicy,
                subtask == null ? DeliveryMode.PATCH : subtask.deliveryMode(),
                subtask == null ? List.of() : subtask.changes()
        );
        initializeTranscript(
                toolSessionState,
                projectPath,
                subtask,
                taskPackage,
                contractView,
                qualityPlan,
                feedback,
                coderContextMarkdown
        );
        List<LlmToolDefinition> toolDefinitions = toolRegistry.toolDefinitions(permissionContext, permissionPolicy);

        for (int turn = 1; turn <= maxToolTurns; turn++) {
            toolContext.appendEvent("实现阶段｜tool-loop｜轮次开始｜子任务=%s｜轮次=%d/%d"
                    .formatted(subtask.title(), turn, maxToolTurns));
            LlmChatResponse response;
            try {
                response = chatCapableLlmProvider.chat(new LlmChatRequest(
                        toolSessionState.transcript(),
                        toolDefinitions,
                        LlmOptions.withOutputBudgetRatio(Map.of(), GenerationBudgetProfile.fullBudgetRatio()),
                        ModelRole.IMPLEMENTATION
                ));
            } catch (Exception exception) {
                GenerationFailureType failureType = GENERATION_FAILURE_CLASSIFIER.classify(exception);
                throw GenerationFailureExceptions.create(
                        subtask.title(),
                        subtask.deliveryMode().name(),
                        "tool-loop",
                        failureType,
                        turn,
                        true,
                        failureType == GenerationFailureType.OUTPUT_TRUNCATED
                                ? "tool loop 输出被截断且没有可续跑内容。"
                                : "tool loop 调用模型失败。",
                        exception.getMessage(),
                        "请保留当前子任务状态，继续在当前子任务上修复。"
                );
            }
            if (response.toolCalls() == null || response.toolCalls().isEmpty()) {
                if (!response.content().isBlank()) {
                    toolSessionState.appendTranscript(LlmChatMessage.assistant(response.content()));
                    if (isTruncated(response)) {
                        toolSessionState.appendTranscript(LlmChatMessage.user(truncationContinuationPrompt(subtask)));
                        toolContext.appendEvent("实现阶段｜tool-loop｜截断续跑｜子任务=%s｜轮次=%d/%d｜模式=assistant-continuation"
                                .formatted(subtask.title(), turn, maxToolTurns));
                        continue;
                    }
                }
                if (response.content().isBlank()) {
                    throw GenerationFailureExceptions.create(
                            subtask.title(),
                            subtask.deliveryMode().name(),
                            "tool-loop",
                            GenerationFailureType.MODEL_OUTPUT_INVALID,
                            turn,
                            true,
                            "tool loop 返回了空 assistant 响应，无法继续收敛。",
                            "assistant response had no content and no tool calls",
                            "请保留当前子任务状态，只补齐当前轮缺失的 assistant 输出或工具调用。"
                    );
                }
                assertDeclaredChangesSatisfied(projectPath, subtask, toolContext, turn);
                toolContext.appendEvent("实现阶段｜tool-loop｜轮次完成｜子任务=%s｜轮次=%d/%d｜触发工具=0"
                        .formatted(subtask.title(), turn, maxToolTurns));
                return new ImplementationToolLoopResult(
                        response.content(),
                        List.copyOf(toolContext.touchedPaths()),
                        toolContext.mutationRecords()
                );
            }
            toolSessionState.appendTranscript(LlmChatMessage.assistantToolCalls(response.content(), response.toolCalls()));
            List<ImplementationToolResultMessage> rawResults;
            try {
                rawResults = executeToolCalls(response.toolCalls(), toolContext);
            } catch (FatalToolExecutionException exception) {
                throw GenerationFailureExceptions.create(
                        subtask.title(),
                        subtask.deliveryMode().name(),
                        "tool-loop",
                        GenerationFailureType.VALIDATION_FAILED,
                        turn,
                        true,
                        exception.summary(),
                        exception.evidence(),
                        exception.retryHint().isBlank()
                                ? "请保留当前子任务状态，只修复当前 fatal tool error，不要重新开始整个子任务。"
                                : exception.retryHint()
                );
            }
            toolSessionState.resetTranscript(resultBudgetManager.appendToolResults(
                    toolSessionState.transcript(),
                    toolContext.toolResultsDirectory(),
                    rawResults,
                    toolSessionState.resultReplacementState()
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

    private void assertDeclaredChangesSatisfied(
            Path projectPath,
            Subtask subtask,
            ImplementationToolContext toolContext,
            int turn
    ) {
        if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
            return;
        }
        Map<Path, PathMutationSummary> mutationsByPath = collectMutationsByPath(toolContext.mutationRecords());
        List<String> unsatisfied = new ArrayList<>();
        for (FileChange change : subtask.changes()) {
            if (change == null || change.path() == null || change.path().isBlank() || change.action() == null) {
                continue;
            }
            Path relativePath = Path.of(change.path()).normalize();
            Path absolutePath = projectPath.resolve(relativePath).normalize();
            boolean exists = toolContext.exists(absolutePath);
            PathMutationSummary mutationSummary = mutationsByPath.get(relativePath);
            FileStateSnapshot currentState = toolContext.captureFileState(absolutePath);
            boolean satisfied = switch (change.action()) {
                case WRITE -> writeSatisfied(mutationSummary, currentState);
                case DELETE -> deleteSatisfied(mutationSummary, exists);
            };
            if (!satisfied) {
                unsatisfied.add("""
                        path=%s, action=%s, exists=%s, baselineExists=%s, baselineHash=%s, currentHash=%s, mutations=%s, reason=%s
                        """.formatted(
                        relativePath.toString().replace('\\', '/'),
                        change.action().name(),
                        exists,
                        mutationSummary != null && mutationSummary.beforeExists(),
                        mutationSummary == null ? "" : mutationSummary.beforeHash(),
                        currentState.contentHash(),
                        mutationSummary == null || mutationSummary.operations().isEmpty() ? "[]" : mutationSummary.operations(),
                        change.reason() == null ? "" : change.reason().trim()
                ).trim());
            }
        }
        if (unsatisfied.isEmpty()) {
            return;
        }
        toolContext.appendEvent("实现阶段｜tool-loop｜交付契约未满足｜子任务=%s｜轮次=%d｜缺口=%d"
                .formatted(subtask.title(), turn, unsatisfied.size()));
        throw GenerationFailureExceptions.create(
                subtask.title(),
                subtask.deliveryMode().name(),
                "tool-loop",
                GenerationFailureType.NO_MATERIAL_CHANGE,
                turn,
                true,
                "tool loop 结束时，当前子任务声明的文件交付契约未满足。",
                """
                        terminalMode=assistant-only
                        declaredChanges=%s
                        unsatisfiedChanges=%s
                        """.formatted(
                        summarizeDeclaredChanges(subtask.changes()),
                        String.join(" | ", unsatisfied)
                ).trim(),
                "请继续当前子任务，只通过工具把缺失的文件写入、更新或删除到位，不要只输出口头完成说明。"
        );
    }

    private void initializeTranscript(
            ImplementationToolSessionState toolSessionState,
            Path projectPath,
            Subtask subtask,
            TaskPackage taskPackage,
            ContractView contractView,
            QualityPlan qualityPlan,
            String feedback,
            String coderContextMarkdown
    ) {
        if (toolSessionState == null) {
            return;
        }
        if (toolSessionState.transcript().isEmpty()) {
            toolSessionState.resetTranscript(List.of(
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
            toolSessionState.appendTranscript(LlmChatMessage.user(promptBuilder.continuationPrompt(feedback)));
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
            ImplementationTool batchTool = toolRegistry.tool(batch.get(0).name());
            boolean concurrent = batch.size() > 1 && batchTool != null && batchTool.concurrencySafe();
            if (concurrent) {
                List<CompletableFuture<ImplementationToolResultMessage>> futures = batch.stream()
                        .map(call -> CompletableFuture.supplyAsync(() -> executeToolCall(call, context), toolExecutor))
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
            ImplementationTool tool = toolRegistry.tool(toolCall.name());
            boolean concurrencySafe = tool != null && tool.concurrencySafe();
            if (concurrencySafe && !batches.isEmpty()) {
                List<LlmToolCall> lastBatch = batches.get(batches.size() - 1);
                ImplementationTool firstTool = toolRegistry.tool(lastBatch.get(0).name());
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
        ImplementationTool tool = toolRegistry.resolveVisibleTool(
                toolCall.name(),
                context.permissionContext(),
                permissionPolicy
        );
        if (tool == null) {
            return new ImplementationToolResultMessage(
                    toolCall.id(),
                    toolCall.name(),
                    renderFailure(
                            objectMapper,
                            toolRegistry.hasTool(toolCall.name())
                                    ? "Tool is not available in the current subtask scope: " + toolCall.name()
                                    : "Unknown tool: " + toolCall.name()
                    ),
                    8_000
            );
        }
        context.appendEvent("实现阶段｜tool-call｜开始｜工具=%s｜参数=%s".formatted(tool.name(), toolCall.arguments()));
        ToolInvocationResult result;
        try {
            result = tool.invoke(toolCall, context);
        } catch (FatalToolExecutionException exception) {
            throw exception;
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

    private boolean isTruncated(LlmChatResponse response) {
        return response != null && DONE_REASON_LENGTH.equalsIgnoreCase(response.doneReason());
    }

    private Map<Path, PathMutationSummary> collectMutationsByPath(List<FileMutationRecord> mutationRecords) {
        Map<Path, PathMutationSummary> mutationsByPath = new LinkedHashMap<>();
        if (mutationRecords == null || mutationRecords.isEmpty()) {
            return mutationsByPath;
        }
        for (FileMutationRecord mutationRecord : mutationRecords) {
            if (mutationRecord == null || mutationRecord.relativePath() == null || mutationRecord.operation() == null) {
                continue;
            }
            Path relativePath = mutationRecord.relativePath().normalize();
            mutationsByPath.compute(relativePath, (ignored, existing) -> {
                if (existing == null) {
                    EnumSet<ToolLoopMutationOperation> operations = EnumSet.noneOf(ToolLoopMutationOperation.class);
                    operations.add(mutationRecord.operation());
                    return new PathMutationSummary(
                            mutationRecord.beforeExists(),
                            mutationRecord.beforeHash(),
                            mutationRecord.afterExists(),
                            mutationRecord.afterHash(),
                            operations
                    );
                }
                EnumSet<ToolLoopMutationOperation> operations = EnumSet.copyOf(existing.operations());
                operations.add(mutationRecord.operation());
                return new PathMutationSummary(
                        existing.beforeExists(),
                        existing.beforeHash(),
                        mutationRecord.afterExists(),
                        mutationRecord.afterHash(),
                        operations
                );
            });
        }
        return mutationsByPath;
    }

    private boolean writeSatisfied(PathMutationSummary mutationSummary, FileStateSnapshot currentState) {
        if (mutationSummary == null || currentState == null || !currentState.exists()) {
            return false;
        }
        return mutationSummary.beforeExists() != currentState.exists()
                || !mutationSummary.beforeHash().equals(currentState.contentHash());
    }

    private boolean deleteSatisfied(PathMutationSummary mutationSummary, boolean currentExists) {
        return mutationSummary != null && mutationSummary.beforeExists() && !currentExists;
    }

    private String truncationContinuationPrompt(Subtask subtask) {
        String title = subtask == null || subtask.title() == null || subtask.title().isBlank()
                ? "当前子任务"
                : subtask.title().trim();
        return """
                继续当前子任务，不要重新规划，不要重写已经完成的内容。
                你上一条 assistant 输出因为长度截断而中断了，现在只继续未完成的部分。
                目标子任务：%s

                要求：
                1. 保留当前 transcript 和已有文件状态。
                2. 如果需要调用工具，直接在当前上下文继续调用。
                3. 不要从头复述方案，不要回退成占位实现。
                """.formatted(title).trim();
    }

    private String summarizeDeclaredChanges(List<FileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return "[]";
        }
        return changes.stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank() && change.action() != null)
                .map(change -> "%s:%s".formatted(change.action().name(), change.path().replace('\\', '/')))
                .toList()
                .toString();
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

    private record PathMutationSummary(
            boolean beforeExists,
            String beforeHash,
            boolean afterExists,
            String afterHash,
            EnumSet<ToolLoopMutationOperation> operations
    ) {
    }
}
