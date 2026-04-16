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
import devflow.agent.editing.precise.FileStateSnapshot;
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
import devflow.agent.executor.subtask.ExecutionFileContract;
import devflow.agent.executor.subtask.ExecutionFileContractMaterializer;
import devflow.agent.executor.subtask.ExecutionFileContractMode;
import devflow.agent.executor.subtask.ExecutionFileContractSet;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.implementation.toolloop.FileMutationRecord;
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
    private final ExecutionFileContractMaterializer executionFileContractMaterializer = new ExecutionFileContractMaterializer();
    private final int maxToolTurns;
    private final ExecutorService toolExecutor;

    public ImplementationToolLoopExecutor(
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            int maxToolTurns,
            ImplementationToolPermissionPolicy permissionPolicy,
            ExecutorService toolExecutor
    ) {
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
        this.maxToolTurns = Math.max(1, maxToolTurns);
        this.toolRegistry = ImplementationToolRegistry.defaultRegistry();
        this.permissionPolicy = permissionPolicy;
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
        DeliveryMode activeDeliveryMode = activeDeliveryMode(subtask, executionState);
        List<FileChange> activeChanges = activeChanges(subtask, executionState);
        boolean repairMode = isRepairMode(executionState);
        ExecutionFileContractSet executionFileContract = executionFileContractMaterializer.materialize(projectPath, activeChanges);
        Subtask activeSubtask = scopeSubtask(subtask, activeDeliveryMode, activeChanges);
        TaskPackage activeTaskPackage = materializeTaskPackage(
                activeSubtask,
                taskPackage,
                executionFileContract
        );
        ChatCapableLlmProvider chatCapableLlmProvider = requireChatProvider();
        ImplementationToolSessionState toolSessionState = executionState == null
                ? new ImplementationToolSessionState()
                : executionState.toolSessionState();
        ImplementationToolPermissionContext permissionContext = permissionPolicy.build(
                projectPath,
                executionFileContract,
                activeDeliveryMode,
                repairMode,
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
                activeDeliveryMode
        );
        initializeTranscript(
                toolSessionState,
                projectPath,
                activeSubtask,
                activeTaskPackage,
                contractView,
                qualityPlan,
                feedback,
                coderContextMarkdown
        );
        List<LlmToolDefinition> toolDefinitions = toolRegistry.toolDefinitions(permissionContext, permissionPolicy);
        String completionSummary = completionSummary(activeSubtask);

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
                        activeDeliveryMode.name(),
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
                            activeDeliveryMode.name(),
                            "tool-loop",
                            GenerationFailureType.MODEL_OUTPUT_INVALID,
                            turn,
                            true,
                            "tool loop 返回了空 assistant 响应，无法继续收敛。",
                            "assistant response had no content and no tool calls",
                            "请保留当前子任务状态，只补齐当前轮缺失的 assistant 输出或工具调用。"
                    );
                }
                assertDeclaredChangesSatisfied(projectPath, executionFileContract, toolContext, turn, "assistant-only", repairMode, activeSubtask);
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
                        activeDeliveryMode.name(),
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
        List<String> unsatisfied = collectUnsatisfiedDeclaredChanges(projectPath, executionFileContract, toolContext, repairMode);
        if (unsatisfied.isEmpty()) {
            toolContext.appendEvent("实现阶段｜tool-loop｜声明交付已满足｜子任务=%s｜轮次=%d/%d｜结束=declared-changes-satisfied"
                    .formatted(subtask.title(), maxToolTurns, maxToolTurns));
            return new ImplementationToolLoopResult(
                    completionSummary,
                    List.copyOf(toolContext.touchedPaths()),
                    toolContext.mutationRecords()
            );
        }
        throw GenerationFailureExceptions.create(
                subtask.title(),
                activeDeliveryMode.name(),
                "tool-loop",
                GenerationFailureType.VALIDATION_FAILED,
                maxToolTurns,
                true,
                "子任务在 tool loop 内未能收敛。",
                """
                        tool loop exceeded max turns without a terminal assistant response
                        unsatisfiedChanges=%s
                        """.formatted(String.join(" | ", unsatisfied)).trim(),
                "请根据当前工具错误和验收缺口继续修复，不要重新开始整个子任务。"
        );
    }

    private void assertDeclaredChangesSatisfied(
            Path projectPath,
            ExecutionFileContractSet executionFileContract,
            ImplementationToolContext toolContext,
            int turn,
            String terminalMode,
            boolean workspaceStateClosure,
            Subtask subtask
    ) {
        List<String> unsatisfied = collectUnsatisfiedDeclaredChanges(projectPath, executionFileContract, toolContext, workspaceStateClosure);
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
                        terminalMode=%s
                        closureMode=%s
                        declaredContracts=%s
                        unsatisfiedChanges=%s
                        """.formatted(
                        terminalMode == null || terminalMode.isBlank() ? "assistant-only" : terminalMode,
                        workspaceStateClosure ? "workspace-state" : "mutation-history",
                        summarizeExecutionFileContracts(executionFileContract),
                        String.join(" | ", unsatisfied)
                ).trim(),
                "请继续当前子任务，只通过工具把缺失的文件写入、更新或删除到位，不要只输出口头完成说明。"
        );
    }

    private List<String> collectUnsatisfiedDeclaredChanges(
            Path projectPath,
            ExecutionFileContractSet executionFileContract,
            ImplementationToolContext toolContext,
            boolean workspaceStateClosure
    ) {
        if (executionFileContract == null || executionFileContract.isEmpty()) {
            return List.of();
        }
        Map<Path, PathMutationSummary> mutationsByPath = collectMutationsByPath(toolContext.mutationRecords());
        List<String> unsatisfied = new ArrayList<>();
        for (ExecutionFileContract contract : executionFileContract.contracts()) {
            if (contract == null || contract.relativePath() == null || contract.path().isBlank()) {
                continue;
            }
            Path relativePath = contract.relativePath();
            Path absolutePath = projectPath.resolve(relativePath).normalize();
            boolean exists = toolContext.exists(absolutePath);
            PathMutationSummary mutationSummary = mutationsByPath.get(relativePath);
            FileStateSnapshot currentState = toolContext.captureFileState(absolutePath);
            boolean satisfied = switch (contract.mode()) {
                case CREATE_NEW -> createSatisfied(mutationSummary, currentState);
                case PATCH_EXISTING -> patchSatisfied(mutationSummary, currentState, workspaceStateClosure);
                case DELETE -> deleteSatisfied(mutationSummary, exists, workspaceStateClosure);
            };
            if (!satisfied) {
                unsatisfied.add("""
                        path=%s, contract=%s, exists=%s, baselineExists=%s, baselineHash=%s, currentHash=%s, mutations=%s, closureMode=%s, reason=%s
                        """.formatted(
                        relativePath.toString().replace('\\', '/'),
                        contract.mode().renderToken(),
                        exists,
                        mutationSummary != null && mutationSummary.beforeExists(),
                        mutationSummary == null ? "" : mutationSummary.beforeHash(),
                        currentState.contentHash(),
                        mutationSummary == null || mutationSummary.operations().isEmpty() ? "[]" : mutationSummary.operations(),
                        workspaceStateClosure ? "workspace-state" : "mutation-history",
                        contract.reason()
                ).trim());
            }
        }
        return List.copyOf(unsatisfied);
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

    private boolean createSatisfied(
            PathMutationSummary mutationSummary,
            FileStateSnapshot currentState
    ) {
        if (currentState == null || !currentState.exists() || mutationSummary == null) {
            return false;
        }
        if (!matchesLatestTerminalState(mutationSummary, currentState)) {
            return false;
        }
        return !mutationSummary.beforeExists() && mutationSummary.afterExists();
    }

    private boolean patchSatisfied(
            PathMutationSummary mutationSummary,
            FileStateSnapshot currentState,
            boolean workspaceStateClosure
    ) {
        if (currentState == null || !currentState.exists()) {
            return false;
        }
        if (mutationSummary == null) {
            return false;
        }
        if (!matchesLatestTerminalState(mutationSummary, currentState)) {
            return false;
        }
        if (workspaceStateClosure) {
            return true;
        }
        return mutationSummary.beforeExists() != currentState.exists()
                || !mutationSummary.beforeHash().equals(currentState.contentHash());
    }

    private boolean deleteSatisfied(
            PathMutationSummary mutationSummary,
            boolean currentExists,
            boolean workspaceStateClosure
    ) {
        if (mutationSummary == null) {
            return false;
        }
        if (!matchesLatestTerminalState(mutationSummary, currentExists)) {
            return false;
        }
        if (workspaceStateClosure) {
            return true;
        }
        return mutationSummary.beforeExists() && !currentExists;
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

    private String summarizeExecutionFileContracts(ExecutionFileContractSet executionFileContract) {
        if (executionFileContract == null || executionFileContract.isEmpty()) {
            return "[]";
        }
        return executionFileContract.contracts().stream()
                .map(contract -> "%s:%s".formatted(contract.mode().renderToken(), contract.path()))
                .toList()
                .toString();
    }

    private String completionSummary(Subtask subtask) {
        String title = subtask == null || subtask.title() == null || subtask.title().isBlank()
                ? "当前子任务"
                : subtask.title().trim();
        return "已完成当前子任务的声明文件交付：%s".formatted(title);
    }

    private DeliveryMode activeDeliveryMode(Subtask subtask, SubtaskExecutionState executionState) {
        if (executionState != null && executionState.deliveryMode() != null) {
            return executionState.deliveryMode();
        }
        return subtask == null || subtask.deliveryMode() == null ? DeliveryMode.PATCH : subtask.deliveryMode();
    }

    private List<FileChange> activeChanges(Subtask subtask, SubtaskExecutionState executionState) {
        List<FileChange> declaredChanges = subtask == null || subtask.changes() == null ? List.of() : subtask.changes();
        if (executionState == null) {
            return List.copyOf(declaredChanges);
        }
        return executionState.effectiveChanges(declaredChanges);
    }

    private boolean isRepairMode(SubtaskExecutionState executionState) {
        return executionState != null && executionState.repairRound();
    }

    private boolean matchesLatestTerminalState(PathMutationSummary mutationSummary, FileStateSnapshot currentState) {
        if (mutationSummary == null || currentState == null) {
            return false;
        }
        return mutationSummary.afterExists() == currentState.exists()
                && mutationSummary.afterHash().equals(currentState.contentHash());
    }

    private boolean matchesLatestTerminalState(PathMutationSummary mutationSummary, boolean currentExists) {
        if (mutationSummary == null) {
            return false;
        }
        return mutationSummary.afterExists() == currentExists
                && (!currentExists || mutationSummary.afterHash() != null);
    }

    private Subtask scopeSubtask(Subtask subtask, DeliveryMode deliveryMode, List<FileChange> activeChanges) {
        if (subtask == null) {
            return null;
        }
        return new Subtask(
                subtask.title(),
                subtask.goal(),
                subtask.coverageRefs(),
                subtask.ownedCapabilities(),
                subtask.deferredCapabilities(),
                subtask.acceptanceCriteria(),
                subtask.runnableMilestone(),
                deliveryMode,
                activeChanges
        );
    }

    private record PathMutationSummary(
            boolean beforeExists,
            String beforeHash,
            boolean afterExists,
            String afterHash,
            EnumSet<ToolLoopMutationOperation> operations
    ) {
    }

    private TaskPackage materializeTaskPackage(
            Subtask activeSubtask,
            TaskPackage taskPackage,
            ExecutionFileContractSet executionFileContract
    ) {
        if (taskPackage == null) {
            return new TaskPackage(
                    activeSubtask.title(),
                    activeSubtask.goal(),
                    activeSubtask.deliveryMode().name(),
                    activeSubtask.runnableMilestone(),
                    executionFileContract.ownedFiles(),
                    activeSubtask.coverageRefs(),
                    activeSubtask.ownedCapabilities(),
                    activeSubtask.deferredCapabilities(),
                    activeSubtask.acceptanceCriteria(),
                    List.of(),
                    List.of(),
                    "",
                    executionFileContract,
                    null
            );
        }
        return taskPackage.alignToSubtask(activeSubtask).withExecutionFileContract(executionFileContract);
    }
}
