package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 表示单个子任务对某个文件的最小变更声明。
 * 它只负责描述文件路径、动作、变更原因和编辑作用域，不直接参与生成或校验。
 */
public record FileChange(
        String path,
        ChangeAction action,
        String reason,
        FileEditScope editScope,
        RuntimeOwnershipMode runtimeOwnership,
        boolean hostHtmlPatchRequired
) {
    public FileChange(String path, ChangeAction action, String reason) {
        this(path, action, reason, FileEditScope.AUTO, null, false);
    }

    public FileChange(String path, ChangeAction action, String reason, FileEditScope editScope) {
        this(path, action, reason, editScope, null, false);
    }

    public FileChange(String path, ChangeAction action, String reason, RuntimeOwnershipMode runtimeOwnership) {
        this(path, action, reason, FileEditScope.AUTO, runtimeOwnership, false);
    }

    public FileChange(
            String path,
            ChangeAction action,
            String reason,
            FileEditScope editScope,
            RuntimeOwnershipMode runtimeOwnership
    ) {
        this(path, action, reason, editScope, runtimeOwnership, false);
    }

    public FileEditScope effectiveEditScope() {
        return editScope == null ? FileEditScope.AUTO : editScope;
    }
}
