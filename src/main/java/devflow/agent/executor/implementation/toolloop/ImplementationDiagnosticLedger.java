package devflow.agent.executor.implementation.toolloop;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * implementation tool session 的唯一诊断权威源。
 *
 * <p>这里不做 parse，也不做策略判断，只做：
 * 1. 记录诊断；
 * 2. 维护稳定顺序；
 * 3. 为 snapshot / artifact 提供统一读取口。
 */
public final class ImplementationDiagnosticLedger {

    private final ArrayList<ImplementationDiagnosticRecord> records;

    public ImplementationDiagnosticLedger() {
        this(List.of());
    }

    public ImplementationDiagnosticLedger(List<ImplementationDiagnosticRecord> records) {
        this.records = new ArrayList<>(records == null ? List.of() : records);
    }

    public ImplementationDiagnosticLedger copy() {
        return new ImplementationDiagnosticLedger(records());
    }

    ImplementationDiagnosticRecord record(
            Path relativePath,
            ToolLoopDiagnosticStatus status,
            ImplementationDiagnosticSource source,
            String evidence
    ) {
        ImplementationDiagnosticRecord record = new ImplementationDiagnosticRecord(
                "diag-" + (records.size() + 1),
                relativePath,
                status,
                source,
                evidence,
                System.currentTimeMillis()
        );
        records.add(record);
        return record;
    }

    public void restore(ImplementationDiagnosticRecord record) {
        if (record != null) {
            records.add(record);
        }
    }

    public List<ImplementationDiagnosticRecord> records() {
        return List.copyOf(records);
    }
}
