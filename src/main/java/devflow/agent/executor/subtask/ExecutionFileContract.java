package devflow.agent.executor.subtask;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.editing.FileEditScope;
import devflow.agent.executor.runtime.RuntimeOwnershipMode;
import java.nio.file.Path;

/**
 * 单文件在当前 attempt 内 materialize 后的执行契约。
 *
 * <p>这层把 planning-time 的结构化 change-set 与当前 workspace existence
 * 收成唯一 live contract，避免 prompt / permission / tool / closure
 * 各自再解释一遍 {@code WRITE} 的含义。
 */
public record ExecutionFileContract(
        Path relativePath,
        ExecutionFileContractMode mode,
        FileChange declaredChange
) {

    public ExecutionFileContract {
        relativePath = relativePath == null ? Path.of("") : relativePath.normalize();
        mode = mode == null ? ExecutionFileContractMode.PATCH_EXISTING : mode;
        if (declaredChange == null) {
            declaredChange = new FileChange(relativePath.toString().replace('\\', '/'), ChangeAction.WRITE, "");
        }
    }

    public String path() {
        return relativePath.toString().replace('\\', '/');
    }

    public ChangeAction action() {
        return declaredChange.action();
    }

    public String reason() {
        return declaredChange.reason() == null ? "" : declaredChange.reason().trim();
    }

    public FileEditScope effectiveEditScope() {
        return declaredChange.effectiveEditScope();
    }

    public RuntimeOwnershipMode runtimeOwnership() {
        return declaredChange.runtimeOwnership();
    }

    public boolean hostHtmlPatchRequired() {
        return declaredChange.hostHtmlPatchRequired();
    }

    public boolean writeIntent() {
        return action() == ChangeAction.WRITE;
    }

    public boolean deleteIntent() {
        return action() == ChangeAction.DELETE;
    }
}
