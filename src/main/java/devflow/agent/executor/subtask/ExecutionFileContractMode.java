package devflow.agent.executor.subtask;

/**
 * 当前 attempt 内，单文件的 live execution 语义。
 *
 * <p>它不替代 planning-time 的 {@code ChangeAction}，而是把
 * accepted/effective structured change-set 基于当前 workspace state
 * materialize 成当前 attempt 可执行的唯一文件契约。
 */
public enum ExecutionFileContractMode {
    CREATE_NEW,
    PATCH_EXISTING,
    DELETE;

    public String renderToken() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
