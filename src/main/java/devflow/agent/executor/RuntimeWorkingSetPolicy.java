package devflow.agent.executor;

/**
 * 统一维护 runtime working set 的补充路径策略。
 *
 * <p>HTML 入口相关文件的补充读取是为了降低局部编辑时的上下文盲区，
 * 这里的阈值属于稳定的运行时策略，不应继续散落在 resolver 实现里。
 */
public final class RuntimeWorkingSetPolicy {

    private static final int DEFAULT_MAX_ADJACENT_RUNTIME_FILES = 4;
    private static final String MAX_ADJACENT_RUNTIME_FILES_KEY = "devflow.runtime-working-set.max-adjacent-files";

    private RuntimeWorkingSetPolicy() {
    }

    public static int maxAdjacentRuntimeFiles() {
        String raw = System.getProperty(MAX_ADJACENT_RUNTIME_FILES_KEY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_ADJACENT_RUNTIME_FILES;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : DEFAULT_MAX_ADJACENT_RUNTIME_FILES;
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAX_ADJACENT_RUNTIME_FILES;
        }
    }
}
