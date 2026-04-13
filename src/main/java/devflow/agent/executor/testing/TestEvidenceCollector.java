package devflow.agent.executor.testing;

import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.tools.ToolResult;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.QualityLedger;
import java.util.List;

/**
 * 统一收集测试阶段的结构化证据。
 *
 * <p>这层只负责把自检、整体可运行检查、运行时快照和 testcase 结果
 * 收成单一快照，避免 gate、renderer 和 executor 各自维护一份测试状态。
 */
class TestEvidenceCollector {

    CollectedTestEvidence collect(
            SelfCheckResult selfCheck,
            ArchitectIntegrationCheckResult architectCheck,
            RuntimeSnapshot runtimeSnapshot,
            List<TestCaseResult> caseResults,
            List<ToolResult> toolResults
    ) {
        return collect(selfCheck, architectCheck, runtimeSnapshot, caseResults, toolResults, CoverageLedger.empty(), QualityLedger.empty());
    }

    CollectedTestEvidence collect(
            SelfCheckResult selfCheck,
            ArchitectIntegrationCheckResult architectCheck,
            RuntimeSnapshot runtimeSnapshot,
            List<TestCaseResult> caseResults,
            List<ToolResult> toolResults,
            CoverageLedger coverageLedger,
            QualityLedger qualityLedger
    ) {
        return new CollectedTestEvidence(
                selfCheck,
                architectCheck,
                runtimeSnapshot,
                caseResults == null ? List.of() : List.copyOf(caseResults),
                toolResults == null ? List.of() : List.copyOf(toolResults),
                coverageLedger == null ? CoverageLedger.empty() : coverageLedger,
                qualityLedger == null ? QualityLedger.empty() : qualityLedger
        );
    }
}
