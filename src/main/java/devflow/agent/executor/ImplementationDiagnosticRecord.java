package devflow.agent.executor;

import java.nio.file.Path;

/**
 * implementation 编码内核的结构化诊断记录。
 *
 * <p>诊断和 mutation 必须拆开：
 * mutation 只表达“文件发生了什么变化”；
 * diagnostics 表达“变化后的结构健康状态是什么”。
 */
record ImplementationDiagnosticRecord(
        String diagnosticId,
        Path relativePath,
        ToolLoopDiagnosticStatus status,
        ImplementationDiagnosticSource source,
        String evidence,
        long timestamp
) {

    ImplementationDiagnosticRecord {
        diagnosticId = diagnosticId == null ? "" : diagnosticId;
        relativePath = relativePath == null ? Path.of("") : relativePath.normalize();
        status = status == null ? ToolLoopDiagnosticStatus.UNSUPPORTED : status;
        source = source == null ? ImplementationDiagnosticSource.UNSUPPORTED_LANGUAGE : source;
        evidence = evidence == null ? "" : evidence;
    }
}
