package devflow.agent.executor.implementation.toolloop;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public enum ToolLoopDiagnosticStatus {
    VALID,
    SYNTAX_INVALID,
    UNSUPPORTED,
    DELETED,
    FAILED
}
