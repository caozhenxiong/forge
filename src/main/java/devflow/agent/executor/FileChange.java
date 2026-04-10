package devflow.agent.executor;

/**
 * 表示单个子任务对某个文件的最小变更声明。
 * 它只负责描述文件路径、动作、变更原因和编辑作用域，不直接参与生成或校验。
 */
record FileChange(
        String path,
        ChangeAction action,
        String reason,
        FileEditScope editScope,
        RuntimeOwnershipMode runtimeOwnership
) {
    FileChange(String path, ChangeAction action, String reason) {
        this(path, action, reason, FileEditScope.AUTO, null);
    }

    FileChange(String path, ChangeAction action, String reason, FileEditScope editScope) {
        this(path, action, reason, editScope, null);
    }

    FileChange(String path, ChangeAction action, String reason, RuntimeOwnershipMode runtimeOwnership) {
        this(path, action, reason, FileEditScope.AUTO, runtimeOwnership);
    }

    FileEditScope effectiveEditScope() {
        return editScope == null ? FileEditScope.AUTO : editScope;
    }
}
