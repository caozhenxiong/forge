package devflow.agent.executor;

import java.nio.file.Path;
import java.util.List;

/**
 * tool loop 内统一记录文件变异的结构化副作用。
 *
 * <p>Forge 没有 Claude 的 IDE/LSP 宿主，因此这里收口成仓库内可消费的等价 contract：
 * 1. 变异动作和前后哈希；
 * 2. 结构化 diff；
 * 3. 当前确定性诊断状态与证据。
 */
record FileMutationRecord(
        ToolLoopMutationOperation operation,
        Path relativePath,
        String beforeHash,
        String afterHash,
        List<StructuredPatchHunk> structuredPatch,
        long timestamp,
        ToolLoopDiagnosticStatus diagnosticStatus,
        String diagnosticEvidence
) {

    FileMutationRecord {
        structuredPatch = structuredPatch == null ? List.of() : List.copyOf(structuredPatch);
        diagnosticEvidence = diagnosticEvidence == null ? "" : diagnosticEvidence;
    }
}
