package devflow.agent.executor;

import devflow.agent.quality.QualityPlan;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于能力矩阵回填缺失的 required testcase。
 */
final class CapabilityCoverageBackfillSupport {

    List<TestCaseSpec> backfill(List<TestCaseSpec> planned, List<TestCaseSpec> baselineCases, QualityPlan qualityPlan) {
        List<TestCaseSpec> base = planned == null ? List.of() : planned;
        if (qualityPlan == null || qualityPlan.capabilityMatrix().isEmpty() || !qualityPlan.coveragePolicy().requireCapabilityBackfill()) {
            return List.copyOf(base);
        }
        List<TestCaseSpec> deterministicCases = baselineCases == null ? List.of() : baselineCases;
        List<TestCaseSpec> result = new ArrayList<>(base);
        Set<String> existingIds = new LinkedHashSet<>();
        Set<String> covered = new LinkedHashSet<>();
        for (TestCaseSpec testCase : base) {
            if (testCase == null) {
                continue;
            }
            existingIds.add(testCase.id());
            covered.addAll(testCase.capabilities());
        }
        for (String required : qualityPlan.capabilityMatrix().requiredCapabilityIds()) {
            if (covered.contains(required)) {
                continue;
            }
            TestCaseSpec match = deterministicCases.stream()
                    .filter(testCase -> testCase != null && testCase.capabilities().contains(required))
                    .filter(testCase -> !existingIds.contains(testCase.id()))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                continue;
            }
            result.add(match);
            existingIds.add(match.id());
            covered.addAll(match.capabilities());
        }

        long requiredCount = result.stream().filter(TestCaseSpec::required).count();
        if (requiredCount < qualityPlan.coveragePolicy().minimumRequiredCases()) {
            for (TestCaseSpec deterministicCase : deterministicCases) {
                if (deterministicCase == null || !deterministicCase.required() || existingIds.contains(deterministicCase.id())) {
                    continue;
                }
                result.add(deterministicCase);
                existingIds.add(deterministicCase.id());
                requiredCount++;
                if (requiredCount >= qualityPlan.coveragePolicy().minimumRequiredCases()) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }
}
