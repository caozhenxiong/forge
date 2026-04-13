package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

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
public final class ImplementationExecutionPolicy {

    private static final int DEFAULT_SUBTASK_ATTEMPTS = 3;
    private static final int DEFAULT_PLANNING_PAYLOAD_REPAIR_ATTEMPTS = 3;
    private static final int DEFAULT_PLANNING_UNIT_ATTEMPTS = 3;
    private static final int DEFAULT_FILE_GENERATION_ATTEMPTS = 3;
    private static final int DEFAULT_TOOL_LOOP_TURNS = 12;
    private static final int DEFAULT_MAX_FILES_PER_SUBTASK = 2;
    private static final int DEFAULT_MAX_DELIVERY_POLICY_FILES = 3;
    private static final long DEFAULT_SHELL_TIMEOUT_MS = 30_000L;
    private static final long DEFAULT_MAX_SHELL_TIMEOUT_MS = 120_000L;
    private static final String SUBTASK_ATTEMPTS_KEY = "devflow.implementation.subtask-attempts";
    private static final String PLANNING_PAYLOAD_REPAIR_ATTEMPTS_KEY = "devflow.implementation.planning-payload-repair-attempts";
    private static final String PLANNING_UNIT_ATTEMPTS_KEY = "devflow.implementation.planning-unit-attempts";
    private static final String FILE_GENERATION_ATTEMPTS_KEY = "devflow.implementation.file-generation-attempts";
    private static final String TOOL_LOOP_TURNS_KEY = "devflow.implementation.tool-loop-turns";
    private static final String MAX_FILES_PER_SUBTASK_KEY = "devflow.implementation.max-files-per-subtask";
    private static final String MAX_DELIVERY_POLICY_FILES_KEY = "devflow.implementation.max-delivery-policy-files";
    private static final String DEFAULT_SHELL_TIMEOUT_MS_KEY = "devflow.implementation.default-shell-timeout-ms";
    private static final String MAX_SHELL_TIMEOUT_MS_KEY = "devflow.implementation.max-shell-timeout-ms";

    private ImplementationExecutionPolicy() {
    }

    public static int subtaskAttempts() {
        return readPositiveInt(SUBTASK_ATTEMPTS_KEY, DEFAULT_SUBTASK_ATTEMPTS);
    }

    public static int planningPayloadRepairAttempts() {
        return readPositiveInt(PLANNING_PAYLOAD_REPAIR_ATTEMPTS_KEY, DEFAULT_PLANNING_PAYLOAD_REPAIR_ATTEMPTS);
    }

    public static int planningUnitAttempts() {
        return readPositiveInt(PLANNING_UNIT_ATTEMPTS_KEY, DEFAULT_PLANNING_UNIT_ATTEMPTS);
    }

    public static int fileGenerationAttempts() {
        return readPositiveInt(FILE_GENERATION_ATTEMPTS_KEY, DEFAULT_FILE_GENERATION_ATTEMPTS);
    }

    public static int toolLoopTurns() {
        return readPositiveInt(TOOL_LOOP_TURNS_KEY, DEFAULT_TOOL_LOOP_TURNS);
    }

    public static int maxFilesPerSubtask() {
        return readPositiveInt(MAX_FILES_PER_SUBTASK_KEY, DEFAULT_MAX_FILES_PER_SUBTASK);
    }

    public static int maxDeliveryPolicyFiles() {
        return readPositiveInt(MAX_DELIVERY_POLICY_FILES_KEY, DEFAULT_MAX_DELIVERY_POLICY_FILES);
    }

    public static long defaultShellTimeoutMs() {
        return readPositiveLong(DEFAULT_SHELL_TIMEOUT_MS_KEY, DEFAULT_SHELL_TIMEOUT_MS);
    }

    public static long maxShellTimeoutMs() {
        return Math.max(defaultShellTimeoutMs(), readPositiveLong(MAX_SHELL_TIMEOUT_MS_KEY, DEFAULT_MAX_SHELL_TIMEOUT_MS));
    }

    private static int readPositiveInt(String key, int fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long readPositiveLong(String key, long fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
