package devflow.agent.executor.implementation.render;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

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
            String failureCode,
            String evidence,
            long timestamp
    ) {
    }
}
