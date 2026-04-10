package devflow.agent.executor;

import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.QualityLedger;
import java.util.List;

/**
 * 测试阶段统一证据快照。
 *
 * <p>测试阶段后续的 gate、artifact 渲染和流程裁决都应尽量基于这份
 * 结构化证据，而不是各自重新拼装一份“测试真相”。
 *
 * <p>`toolResults` 现在同时承载：
 * 1. 自检验证层的本地工具结果；
 * 2. testcase 执行层的工具结果。
 */
record CollectedTestEvidence(
        SelfCheckResult selfCheck,
        ArchitectIntegrationCheckResult architectCheck,
        RuntimeSnapshot runtimeSnapshot,
        List<TestCaseResult> caseResults,
        List<ToolResult> toolResults,
        CoverageLedger coverageLedger,
        QualityLedger qualityLedger
) {
    CollectedTestEvidence(
            SelfCheckResult selfCheck,
            ArchitectIntegrationCheckResult architectCheck,
            RuntimeSnapshot runtimeSnapshot,
            List<TestCaseResult> caseResults,
            List<ToolResult> toolResults
    ) {
        this(selfCheck, architectCheck, runtimeSnapshot, caseResults, toolResults, CoverageLedger.empty(), QualityLedger.empty());
    }
}
