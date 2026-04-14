package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.quality.CapabilityIds;
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
    private final TestCaseBehaviorRepairSupport behaviorRepairSupport;
    private final ObservedInteractionTestCaseBuilder observedInteractionTestCaseBuilder;
    private final TestCaseCapabilityInferencer capabilityInferencer = new TestCaseCapabilityInferencer();

    TestCasePlanSanitizer() {
        this(new TestPlanningPolicy());
    }

    TestCasePlanSanitizer(TestPlanningPolicy testPlanningPolicy) {
        this.behaviorRepairSupport = new TestCaseBehaviorRepairSupport(testPlanningPolicy);
        this.observedInteractionTestCaseBuilder = new ObservedInteractionTestCaseBuilder(testPlanningPolicy);
    }

    List<TestCaseSpec> sanitize(
            List<PlannedTestCasePayload> rawCases,
            List<TestCaseSpec> baseCases,
            String defaultEntry,
            RuntimeSnapshot runtimeSnapshot,
            UiRuntimeContract runtimeContract,
            DocumentLanguage language
    ) {
        if (rawCases == null || rawCases.isEmpty()) {
            return observedInteractionTestCaseBuilder.strengthenCases(baseCases, runtimeSnapshot, runtimeContract, language);
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
                    parseCapabilities(raw.capabilities()),
                    CapabilityIds.normalize(raw.observationTargetId()),
                    TestObservationTrigger.fromWireValue(raw.observationTrigger()),
                    TestObservationComparison.fromWireValue(raw.observationComparison())
            ));
        }
        List<TestCaseSpec> repaired = behaviorRepairSupport.repairCases(result.isEmpty() ? baseCases : result, runtimeSnapshot, runtimeContract);
        List<TestCaseSpec> strengthened = observedInteractionTestCaseBuilder.strengthenCases(
                inferCapabilities(repaired),
                runtimeSnapshot,
                runtimeContract,
                language
        );
        return behaviorRepairSupport.repairCases(strengthened, runtimeSnapshot, runtimeContract);
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
            List<String> capabilities = testCase.capabilities().isEmpty()
                    ? capabilityInferencer.infer(testCase)
                    : testCase.capabilities();
            TestObservationProfile observationProfile = inferObservationProfile(testCase, capabilities);
            inferred.add(new TestCaseSpec(
                    testCase.id(),
                    testCase.title(),
                    testCase.type(),
                    testCase.required(),
                    testCase.entry(),
                    testCase.preconditions(),
                    testCase.expected(),
                    testCase.steps(),
                    capabilities,
                    observationProfile.targetId(),
                    observationProfile.trigger(),
                    observationProfile.comparison()
            ));
        }
        return List.copyOf(inferred);
    }

    private List<String> parseCapabilities(List<String> values) {
        return CapabilityIds.normalizeList(values);
    }

    private TestObservationProfile inferObservationProfile(TestCaseSpec testCase, List<String> capabilities) {
        String targetId = CapabilityIds.normalize(testCase.observationTargetId());
        TestObservationTrigger trigger = testCase.observationTrigger();
        TestObservationComparison comparison = testCase.observationComparison();
        if (comparison == TestObservationComparison.NONE) {
            comparison = inferObservationComparison(testCase.steps());
        }
        if (trigger == TestObservationTrigger.NONE && comparison.requiresSnapshotComparison()) {
            trigger = inferObservationTrigger(testCase.steps());
        }
        if (targetId.isBlank() && comparison.requiresSnapshotComparison()) {
            if (capabilities.contains(CapabilityIds.PRIMARY_INTERACTION) || hasInteractiveAction(testCase.steps())) {
                targetId = CapabilityIds.PRIMARY_INTERACTION;
            } else if (capabilities.contains(CapabilityIds.PRIMARY_VISUAL_SURFACE) || hasObservationAction(testCase.steps())) {
                targetId = CapabilityIds.PRIMARY_VISUAL_SURFACE;
            }
        }
        return new TestObservationProfile(targetId, trigger, comparison);
    }

    private TestObservationTrigger inferObservationTrigger(List<TestStepSpec> steps) {
        if (steps == null || steps.isEmpty()) {
            return TestObservationTrigger.NONE;
        }
        int assertIndex = lastObservableAssertionIndex(steps);
        if (assertIndex < 0) {
            return TestObservationTrigger.NONE;
        }
        if (lastDriverInteractionIndex(steps, assertIndex) >= 0) {
            return TestObservationTrigger.AFTER_INTERACTION;
        }
        int lastInteractiveIndex = lastInteractiveIndex(steps, assertIndex);
        if (lastInteractiveIndex < 0) {
            return TestObservationTrigger.AFTER_WAIT;
        }
        int snapshotIndex = lastSnapshotIndex(steps, assertIndex);
        return snapshotIndex >= 0 && snapshotIndex < lastInteractiveIndex
                ? TestObservationTrigger.AFTER_INTERACTION
                : TestObservationTrigger.AFTER_WAIT;
    }

    private TestObservationComparison inferObservationComparison(List<TestStepSpec> steps) {
        if (steps == null || steps.isEmpty()) {
            return TestObservationComparison.NONE;
        }
        for (TestStepSpec step : steps) {
            if (step == null || step.action() == null) {
                continue;
            }
            if (step.action() == TestStepAction.ASSERT_CANVAS_HASH_UNCHANGED
                    || step.action() == TestStepAction.ASSERT_DOM_SIGNATURE_UNCHANGED) {
                return TestObservationComparison.UNCHANGED;
            }
            if (step.action() == TestStepAction.ASSERT_CANVAS_HASH_CHANGED
                    || step.action() == TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED) {
                return TestObservationComparison.CHANGED;
            }
        }
        return TestObservationComparison.NONE;
    }

    private boolean hasInteractiveAction(List<TestStepSpec> steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        return steps.stream().anyMatch(step -> step != null && step.action() != null && step.action().isInteractive());
    }

    private boolean hasObservationAction(List<TestStepSpec> steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        return steps.stream().anyMatch(step -> step != null && (
                step.action() == TestStepAction.SNAPSHOT_CANVAS_HASH
                        || step.action() == TestStepAction.SNAPSHOT_DOM_SIGNATURE
                        || step.action() != null && step.action().isObservablePostcondition()
        ));
    }

    private int lastObservableAssertionIndex(List<TestStepSpec> steps) {
        for (int index = steps.size() - 1; index >= 0; index--) {
            TestStepSpec step = steps.get(index);
            if (step != null && step.action() != null && step.action().isObservablePostcondition()) {
                return index;
            }
        }
        return -1;
    }

    private int lastDriverInteractionIndex(List<TestStepSpec> steps, int beforeIndex) {
        for (int index = Math.min(beforeIndex, steps.size()) - 1; index >= 0; index--) {
            TestStepSpec step = steps.get(index);
            if (step == null || step.action() == null || !step.action().isInteractive()) {
                continue;
            }
            if (step.action() == TestStepAction.PRESS_KEY) {
                return index;
            }
            if (step.action() == TestStepAction.CLICK && step.semantic() != TestStepSemantic.RUN_STATE_ENTRY) {
                return index;
            }
        }
        return -1;
    }

    private int lastInteractiveIndex(List<TestStepSpec> steps, int beforeIndex) {
        for (int index = Math.min(beforeIndex, steps.size()) - 1; index >= 0; index--) {
            TestStepSpec step = steps.get(index);
            if (step != null && step.action() != null && step.action().isInteractive()) {
                return index;
            }
        }
        return -1;
    }

    private int lastSnapshotIndex(List<TestStepSpec> steps, int beforeIndex) {
        for (int index = Math.min(beforeIndex, steps.size()) - 1; index >= 0; index--) {
            TestStepSpec step = steps.get(index);
            if (step == null) {
                continue;
            }
            if (step.action() == TestStepAction.SNAPSHOT_CANVAS_HASH
                    || step.action() == TestStepAction.SNAPSHOT_DOM_SIGNATURE) {
                return index;
            }
        }
        return -1;
    }

    private record TestObservationProfile(
            String targetId,
            TestObservationTrigger trigger,
            TestObservationComparison comparison
    ) {
    }
}
