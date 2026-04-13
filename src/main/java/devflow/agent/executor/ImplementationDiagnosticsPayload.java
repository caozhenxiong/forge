package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

record ImplementationDiagnosticsPayload(
        List<Entry> diagnostics
) {

    ImplementationDiagnosticsPayload {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    record Entry(
            String subtaskTitle,
            String diagnosticId,
            String relativePath,
            String status,
            String source,
            String evidence,
            long timestamp
    ) {
    }
}
