package devflow.agent.executor.gate;

import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.testing.TestCaseResult;

import devflow.agent.quality.CoverageLedger;
import java.util.List;

/**
 * 测试证据 gate 的输入。
 *
 * <p>第一版只关心测试阶段已经收集到的确定性证据：
 * - 自检结果
 * - architect runnable 检查结果
 * - testcase 执行结果
 */
public record TestEvidenceGateInput(
        SelfCheckResult selfCheck,
        ArchitectIntegrationCheckResult architectCheck,
        List<TestCaseResult> caseResults,
        CoverageLedger coverageLedger
) {
    public TestEvidenceGateInput(
            SelfCheckResult selfCheck,
            ArchitectIntegrationCheckResult architectCheck,
            List<TestCaseResult> caseResults
    ) {
        this(selfCheck, architectCheck, caseResults, CoverageLedger.empty());
    }
}
