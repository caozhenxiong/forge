package devflow.agent.executor;

import java.nio.file.Path;
import java.util.List;

/**
 * tool loop 内统一记录文件变异的结构化副作用。
 *
 * <p>这层只表达文件变异事实：
 * 1. 变异动作和前后哈希；
 * 2. 结构化 diff；
 *
 * <p>diagnostics 已独立收敛到 session 级 ledger，
 * 不再继续塞在 mutation 里形成第二份真相源。
 */
record FileMutationRecord(
        ToolLoopMutationOperation operation,
        Path relativePath,
        boolean beforeExists,
        String beforeHash,
        boolean afterExists,
        String afterHash,
        List<StructuredPatchHunk> structuredPatch,
        long timestamp
) {

    FileMutationRecord {
        structuredPatch = structuredPatch == null ? List.of() : List.copyOf(structuredPatch);
    }
}
