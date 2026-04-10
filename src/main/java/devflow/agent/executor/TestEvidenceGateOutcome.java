package devflow.agent.executor;

/**
 * 测试证据 gate 的统一输出。
 */
record TestEvidenceGateOutcome(
        GateReport report,
        boolean finalPassed,
        long totalCases,
        long passedCases,
        long requiredFailedCases,
        long requiredBlockedCases,
        String summary
) {
}
