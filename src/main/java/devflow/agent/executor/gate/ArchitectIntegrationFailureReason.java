package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.runtime.*;

/**
 * 整体可运行检查的稳定失败原因。
 */
public enum ArchitectIntegrationFailureReason {
    ENTRY_MISSING("entry-missing"),
    SURFACE_MISSING("surface-missing"),
    RUNTIME_WIRING_INVALID("runtime-wiring-invalid"),
    IMPLEMENTATION_INCOMPLETE("implementation-incomplete");

    private final String wireValue;

    ArchitectIntegrationFailureReason(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
