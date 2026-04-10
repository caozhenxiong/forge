package devflow.agent.executor;
import devflow.agent.quality.CapabilitySurface;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试规划结果清洗器。
 *
 * <p>它负责：
 * 1. 把模型返回的 payload 清洗成稳定的 `TestCaseSpec`；
 * 2. 对 required 交互用例补充可观察后置条件；
 * 3. 在模型输出为空或过弱时保留确定性基础用例。
 */
final class TestCasePlanSanitizer {

    private final TestCaseStepSanitizer stepSanitizer = new TestCaseStepSanitizer();
    private final TestCaseBehaviorRepairSupport behaviorRepairSupport = new TestCaseBehaviorRepairSupport();
    private final ObservedInteractionTestCaseBuilder observedInteractionTestCaseBuilder = new ObservedInteractionTestCaseBuilder();
    private final TestCaseCapabilityInferencer capabilityInferencer = new TestCaseCapabilityInferencer();

    List<TestCaseSpec> sanitize(
            List<PlannedTestCasePayload> rawCases,
            List<TestCaseSpec> baseCases,
            String defaultEntry,
            RuntimeSnapshot runtimeSnapshot
    ) {
        if (rawCases == null || rawCases.isEmpty()) {
            return observedInteractionTestCaseBuilder.strengthenCases(baseCases, runtimeSnapshot);
        }
        List<TestCaseSpec> result = new ArrayList<>();
        for (PlannedTestCasePayload raw : rawCases) {
            if (raw == null || raw.id() == null || raw.id().isBlank() || raw.title() == null || raw.title().isBlank()) {
                continue;
            }
            List<TestStepSpec> steps = stepSanitizer.sanitizeSteps(raw.steps());
            if (steps.isEmpty()) {
                continue;
            }
            result.add(new TestCaseSpec(
                    raw.id().trim(),
                    raw.title().trim(),
                    blank(raw.type()).isBlank() ? "smoke" : raw.type().trim(),
                    raw.required() == null || raw.required(),
                    blank(raw.entry()).isBlank() ? defaultEntry : raw.entry().trim(),
                    blank(raw.preconditions()),
                    blank(raw.expected()),
                    steps,
                    parseCapabilities(raw.capabilities())
            ));
        }
        List<TestCaseSpec> repaired = behaviorRepairSupport.repairCases(result.isEmpty() ? baseCases : result, runtimeSnapshot);
        return observedInteractionTestCaseBuilder.strengthenCases(inferCapabilities(repaired), runtimeSnapshot);
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private List<TestCaseSpec> inferCapabilities(List<TestCaseSpec> cases) {
        List<TestCaseSpec> inferred = new ArrayList<>();
        for (TestCaseSpec testCase : cases) {
            if (testCase == null) {
                continue;
            }
            List<CapabilitySurface> capabilities = testCase.capabilities().isEmpty()
                    ? capabilityInferencer.infer(testCase)
                    : testCase.capabilities();
            inferred.add(new TestCaseSpec(
                    testCase.id(),
                    testCase.title(),
                    testCase.type(),
                    testCase.required(),
                    testCase.entry(),
                    testCase.preconditions(),
                    testCase.expected(),
                    testCase.steps(),
                    capabilities
            ));
        }
        return List.copyOf(inferred);
    }

    private List<CapabilitySurface> parseCapabilities(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(CapabilitySurface::fromWireValue)
                .filter(surface -> surface != null)
                .distinct()
                .toList();
    }
}
