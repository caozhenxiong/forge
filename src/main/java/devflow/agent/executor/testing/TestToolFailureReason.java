package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 测试工具选择失败时的稳定原因码。
 */
public enum TestToolFailureReason {
    UNSUPPORTED_EXECUTOR("unsupported-executor"),
    EXECUTION_CONTRACT_UNMET("execution-contract-unmet"),
    ENTRY_MISSING("entry-missing"),
    LAUNCH_UNAVAILABLE("launch-unavailable"),
    SURFACE_MISSING("surface-missing"),
    IMPLEMENTATION_INCOMPLETE("implementation-incomplete");

    private final String wireValue;

    TestToolFailureReason(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
