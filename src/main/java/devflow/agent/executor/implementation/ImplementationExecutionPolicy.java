package devflow.agent.executor.implementation;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一维护 implementation 主链的稳定执行策略。
 *
 * <p>这里收敛的是“流程级默认值”，不是业务语义：
 * 子任务尝试次数、planning 内部重试次数、单次计划允许携带的文件数等，
 * 都应由同一个策略源提供，避免继续散落在门面编排器和子组件里。
 *
 * <p>后续如果要进一步配置化，应优先从这里下沉到 properties，
 * 而不是重新把数字写回执行器、planner 或 coordinator。
 */
@ConfigurationProperties(prefix = "devflow.implementation")
public record ImplementationExecutionPolicy(
        int subtaskAttempts,
        int planningPayloadRepairAttempts,
        int planningUnitAttempts,
        int fileGenerationAttempts,
        int toolLoopTurns,
        int maxFilesPerSubtask,
        int maxDeliveryPolicyFiles,
        long defaultShellTimeoutMs,
        long maxShellTimeoutMs
) {

    private static final int DEFAULT_SUBTASK_ATTEMPTS = 3;
    private static final int DEFAULT_PLANNING_PAYLOAD_REPAIR_ATTEMPTS = 3;
    private static final int DEFAULT_PLANNING_UNIT_ATTEMPTS = 3;
    private static final int DEFAULT_FILE_GENERATION_ATTEMPTS = 3;
    private static final int DEFAULT_TOOL_LOOP_TURNS = 12;
    private static final int DEFAULT_MAX_FILES_PER_SUBTASK = 2;
    private static final int DEFAULT_MAX_DELIVERY_POLICY_FILES = 3;
    private static final long DEFAULT_SHELL_TIMEOUT_MS = 30_000L;
    private static final long DEFAULT_MAX_SHELL_TIMEOUT_MS = 120_000L;

    public ImplementationExecutionPolicy() {
        this(
                DEFAULT_SUBTASK_ATTEMPTS,
                DEFAULT_PLANNING_PAYLOAD_REPAIR_ATTEMPTS,
                DEFAULT_PLANNING_UNIT_ATTEMPTS,
                DEFAULT_FILE_GENERATION_ATTEMPTS,
                DEFAULT_TOOL_LOOP_TURNS,
                DEFAULT_MAX_FILES_PER_SUBTASK,
                DEFAULT_MAX_DELIVERY_POLICY_FILES,
                DEFAULT_SHELL_TIMEOUT_MS,
                DEFAULT_MAX_SHELL_TIMEOUT_MS
        );
    }

    public ImplementationExecutionPolicy {
        subtaskAttempts = normalizePositive(subtaskAttempts, DEFAULT_SUBTASK_ATTEMPTS);
        planningPayloadRepairAttempts = normalizePositive(
                planningPayloadRepairAttempts,
                DEFAULT_PLANNING_PAYLOAD_REPAIR_ATTEMPTS
        );
        planningUnitAttempts = normalizePositive(planningUnitAttempts, DEFAULT_PLANNING_UNIT_ATTEMPTS);
        fileGenerationAttempts = normalizePositive(fileGenerationAttempts, DEFAULT_FILE_GENERATION_ATTEMPTS);
        toolLoopTurns = normalizePositive(toolLoopTurns, DEFAULT_TOOL_LOOP_TURNS);
        maxFilesPerSubtask = normalizePositive(maxFilesPerSubtask, DEFAULT_MAX_FILES_PER_SUBTASK);
        maxDeliveryPolicyFiles = normalizePositive(maxDeliveryPolicyFiles, DEFAULT_MAX_DELIVERY_POLICY_FILES);
        defaultShellTimeoutMs = normalizePositive(defaultShellTimeoutMs, DEFAULT_SHELL_TIMEOUT_MS);
        maxShellTimeoutMs = Math.max(
                defaultShellTimeoutMs,
                normalizePositive(maxShellTimeoutMs, DEFAULT_MAX_SHELL_TIMEOUT_MS)
        );
    }

    private static int normalizePositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static long normalizePositive(long value, long fallback) {
        return value > 0 ? value : fallback;
    }
}
