package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.quality.CapabilityMatrixEntry;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.CoverageLedgerEntry;
import devflow.agent.quality.CoverageLedgerStatus;
import devflow.agent.quality.QualityPlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 testcase 计划与执行结果收成能力覆盖账本。
 */
final class CoverageLedgerBuilder {

    CoverageLedger build(TestCasePlan plan, List<TestCaseResult> caseResults) {
        if (plan == null || plan.qualityPlan() == null || plan.qualityPlan().capabilityMatrix().isEmpty()) {
            return CoverageLedger.empty();
        }
        Map<String, TestCaseResult> resultById = new LinkedHashMap<>();
        if (caseResults != null) {
            for (TestCaseResult caseResult : caseResults) {
                if (caseResult != null && caseResult.id() != null && !caseResult.id().isBlank()) {
                    resultById.put(caseResult.id(), caseResult);
                }
            }
        }
        List<CoverageLedgerEntry> entries = new ArrayList<>();
        QualityPlan qualityPlan = plan.qualityPlan();
        for (CapabilityMatrixEntry matrixEntry : qualityPlan.capabilityMatrix().entries()) {
            if (matrixEntry == null || matrixEntry.capabilityId().isBlank()) {
                continue;
            }
            List<TestCaseSpec> matchingCases = plan.cases().stream()
                    .filter(testCase -> testCase != null && testCase.capabilities().contains(matrixEntry.capabilityId()))
                    .toList();
            List<String> caseIds = matchingCases.stream().map(TestCaseSpec::id).toList();
            CoverageLedgerStatus status = resolveStatus(caseIds, resultById);
            String note = switch (status) {
                case MISSING -> "no testcase covers this capability";
                case FAILED -> "covered testcase exists but no passing execution evidence";
                case COVERED -> "";
            };
            entries.add(new CoverageLedgerEntry(
                    matrixEntry.capabilityId(),
                    matrixEntry.required(),
                    status,
                    caseIds,
                    note
            ));
        }
        return new CoverageLedger(entries);
    }

    private CoverageLedgerStatus resolveStatus(List<String> caseIds, Map<String, TestCaseResult> resultById) {
        if (caseIds == null || caseIds.isEmpty()) {
            return CoverageLedgerStatus.MISSING;
        }
        boolean hasPassed = false;
        boolean hasRecordedResult = false;
        for (String caseId : caseIds) {
            TestCaseResult result = resultById.get(caseId);
            if (result == null) {
                continue;
            }
            hasRecordedResult = true;
            if (result.passed()) {
                hasPassed = true;
                break;
            }
        }
        if (hasPassed) {
            return CoverageLedgerStatus.COVERED;
        }
        return hasRecordedResult ? CoverageLedgerStatus.FAILED : CoverageLedgerStatus.MISSING;
    }
}
