package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.runtime.*;

/**
 * 测试证据 gate 的统一输出。
 */
public record TestEvidenceGateOutcome(
        GateReport report,
        boolean finalPassed,
        long totalCases,
        long passedCases,
        long requiredFailedCases,
        long requiredBlockedCases,
        String summary
) {
}
