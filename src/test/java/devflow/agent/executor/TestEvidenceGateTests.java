package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.testing.TestCaseResult;
import devflow.agent.executor.testing.TestCaseStatus;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.CoverageLedgerEntry;
import devflow.agent.quality.CoverageLedgerStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEvidenceGateTests {

    private final TestEvidenceGate gate = new TestEvidenceGate();

    @Test
    void passesWhenSelfCheckArchitectCheckAndRequiredCasesAllPass() {
        TestEvidenceGateOutcome outcome = gate.evaluateDetailed(
                new TestEvidenceGateInput(
                        new SelfCheckResult(true, "ok", "details"),
                        ArchitectIntegrationCheckResult.success(),
                        List.of(new TestCaseResult("TC-1", "必测", TestCaseStatus.PASSED, true, "", "", ""))
                ),
                DocumentLanguage.ZH
        );

        assertTrue(outcome.report().passed());
        assertTrue(outcome.finalPassed());
        assertEquals(1, outcome.totalCases());
    }

    @Test
    void missingTestCasesRequestsStageReplan() {
        TestEvidenceGateOutcome outcome = gate.evaluateDetailed(
                new TestEvidenceGateInput(
                        new SelfCheckResult(true, "ok", "details"),
                        ArchitectIntegrationCheckResult.success(),
                        List.of()
                ),
                DocumentLanguage.ZH
        );

        assertFalse(outcome.report().passed());
        assertEquals(GateFailureDisposition.REPLAN_CURRENT_STAGE, outcome.report().issues().get(0).disposition());
    }

    @Test
    void blockedRequiredCasesEscalate() {
        TestEvidenceGateOutcome outcome = gate.evaluateDetailed(
                new TestEvidenceGateInput(
                        new SelfCheckResult(true, "ok", "details"),
                        ArchitectIntegrationCheckResult.success(),
                        List.of(new TestCaseResult("TC-1", "必测", TestCaseStatus.BLOCKED, true, "", "entry-missing", "缺少入口"))
                ),
                DocumentLanguage.ZH
        );

        assertFalse(outcome.report().passed());
        assertEquals(GateFailureDisposition.ESCALATE, outcome.report().issues().get(0).disposition());
        assertTrue(outcome.summary().contains("阻塞") || outcome.summary().contains("未执行"));
    }

    @Test
    void failedSelfCheckAlsoEscalates() {
        TestEvidenceGateOutcome outcome = gate.evaluateDetailed(
                new TestEvidenceGateInput(
                        new SelfCheckResult(false, "syntax error", "details"),
                        ArchitectIntegrationCheckResult.success(),
                        List.of(new TestCaseResult("TC-1", "必测", TestCaseStatus.PASSED, true, "", "", ""))
                ),
                DocumentLanguage.ZH
        );

        assertFalse(outcome.report().passed());
        assertTrue(outcome.report().issues().stream().anyMatch(issue -> issue.code().equals("SELF_CHECK_FAILED")));
    }

    @Test
    void missingRequiredCapabilityCoverageAlsoEscalates() {
        TestEvidenceGateOutcome outcome = gate.evaluateDetailed(
                new TestEvidenceGateInput(
                        new SelfCheckResult(true, "ok", "details"),
                        ArchitectIntegrationCheckResult.success(),
                        List.of(new TestCaseResult("TC-1", "必测", TestCaseStatus.PASSED, true, "", "", "")),
                        new CoverageLedger(List.of(
                                new CoverageLedgerEntry(
                                        CapabilityIds.RESET_RESTORES_INITIAL_STATE,
                                        true,
                                        CoverageLedgerStatus.MISSING,
                                        List.of(),
                                        "missing"
                                )
                        ))
                ),
                DocumentLanguage.ZH
        );

        assertFalse(outcome.finalPassed());
        assertTrue(outcome.report().issues().stream().anyMatch(issue -> issue.code().equals("REQUIRED_CAPABILITY_COVERAGE_MISSING")));
    }
}
