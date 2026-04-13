package devflow.agent.executor.testing;

import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.QualityLedger;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestEvidenceCollectorTests {

    @Test
    void collectorBuildsStableEvidenceSnapshot() {
        TestEvidenceCollector collector = new TestEvidenceCollector();
        SelfCheckResult selfCheck = new SelfCheckResult(true, "ok", "details");
        ArchitectIntegrationCheckResult architectCheck = ArchitectIntegrationCheckResult.success();
        RuntimeSnapshot runtimeSnapshot = new RuntimeSnapshot(
                "index.html",
                "Demo",
                120,
                1,
                List.of("#app-root"),
                List.of("lastActionMs"),
                List.of(),
                List.of()
        );
        List<TestCaseResult> caseResults = List.of(
                new TestCaseResult("TC-1", "smoke", TestCaseStatus.PASSED, true, "", "", "")
        );
        List<ToolResult> toolResults = List.of(
                ToolResult.success(ToolName.TEST_CASE_EXECUTION)
        );

        CollectedTestEvidence evidence = collector.collect(selfCheck, architectCheck, runtimeSnapshot, caseResults, toolResults);

        assertEquals(selfCheck, evidence.selfCheck());
        assertEquals(architectCheck, evidence.architectCheck());
        assertEquals(runtimeSnapshot, evidence.runtimeSnapshot());
        assertEquals(caseResults, evidence.caseResults());
        assertEquals(toolResults, evidence.toolResults());
        assertNotNull(evidence.caseResults());
        assertNotNull(evidence.toolResults());
        assertEquals(CoverageLedger.empty(), evidence.coverageLedger());
        assertEquals(QualityLedger.empty(), evidence.qualityLedger());
    }
}
