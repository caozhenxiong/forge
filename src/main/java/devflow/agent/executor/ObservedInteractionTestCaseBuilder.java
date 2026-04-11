package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.quality.CapabilitySurface;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 负责 required 交互用例的可观察后置条件补强。
 *
 * <p>这层把“点击之后必须能观察到状态变化”的策略集中起来，
 * 避免测试规划结果清洗器继续承担 fallback 交互用例构造。
 */
final class ObservedInteractionTestCaseBuilder {

    private final UiRuntimeObservationPolicy observationPolicy = new UiRuntimeObservationPolicy();

    List<TestCaseSpec> strengthenCases(List<TestCaseSpec> cases, UiRuntimeContract runtimeContract) {
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }
        List<TestCaseSpec> strengthened = new ArrayList<>();
        boolean hasRequiredInteractiveCase = false;
        for (TestCaseSpec testCase : cases) {
            List<TestStepSpec> steps = new ArrayList<>(testCase.steps() == null ? List.of() : testCase.steps());
            if (testCase.required() && isInteractive(steps)) {
                hasRequiredInteractiveCase = true;
                if (!hasObservablePostcondition(steps)) {
                    steps = addObservablePostcondition(steps, runtimeContract, testCase.id(), testCase.capabilities());
                }
            }
            strengthened.add(new TestCaseSpec(
                    testCase.id(),
                    testCase.title(),
                    testCase.type(),
                    testCase.required(),
                    testCase.entry(),
                    testCase.preconditions(),
                    testCase.expected(),
                    List.copyOf(steps),
                    testCase.capabilities()
            ));
        }
        if (!hasRequiredInteractiveCase) {
            String selector = selectInteractionSelector(extractSelectors(cases));
            if (selector != null) {
                TestCaseSpec observedCase = buildObservedInteractionCase(
                        cases.getFirst().entry(),
                        selector,
                        runtimeContract,
                        DocumentLanguage.detect(cases.getFirst().title(), cases.getFirst().expected())
                );
                if (observedCase != null) {
                    strengthened.add(observedCase);
                }
            }
        }
        return List.copyOf(strengthened);
    }

    private boolean isInteractive(List<TestStepSpec> steps) {
        return steps.stream().anyMatch(step -> step.action() != null && step.action().isInteractive());
    }

    private boolean hasObservablePostcondition(List<TestStepSpec> steps) {
        return steps.stream().anyMatch(step -> step.action() != null && step.action().isObservablePostcondition());
    }

    private List<TestStepSpec> addObservablePostcondition(
            List<TestStepSpec> steps,
            UiRuntimeContract runtimeContract,
            String caseId,
            List<devflow.agent.quality.CapabilitySurface> capabilities
    ) {
        List<TestStepSpec> strengthened = new ArrayList<>(steps);
        int firstInteractionIndex = -1;
        for (int index = 0; index < strengthened.size(); index++) {
            TestStepAction action = strengthened.get(index).action();
            if (action != null && action.isInteractive()) {
                firstInteractionIndex = index;
                break;
            }
        }
        if (firstInteractionIndex < 0) {
            return strengthened;
        }
        UiObservationTarget target = observationPolicy.requiredTarget(
                runtimeContract,
                capabilities != null && capabilities.contains(devflow.agent.quality.CapabilitySurface.TIMED_STATE_PROGRESSION)
                        ? devflow.agent.quality.CapabilitySurface.TIMED_STATE_PROGRESSION
                        : devflow.agent.quality.CapabilitySurface.PRIMARY_INTERACTION
        );
        if (target == null) {
            return strengthened;
        }
        String snapshotKey = "observed-" + caseId;
        strengthened.add(firstInteractionIndex, observationPolicy.snapshotStep(target, snapshotKey, false));
        if (strengthened.stream().noneMatch(step -> step.action() == TestStepAction.WAIT
                && step.ms() != null
                && step.ms() >= TestPlanningPolicy.defaultStepWaitMs())) {
            strengthened.add(new TestStepSpec(
                    TestStepAction.WAIT,
                    null,
                    null,
                    null,
                    observationPolicy.observationWaitMs(capabilities),
                    null,
                    false
            ));
        }
        strengthened.add(observationPolicy.changedAssertion(target, snapshotKey, false));
        return strengthened;
    }

    private Set<String> extractSelectors(List<TestCaseSpec> cases) {
        Set<String> selectors = new LinkedHashSet<>();
        if (cases == null || cases.isEmpty()) {
            return selectors;
        }
        for (TestCaseSpec testCase : cases) {
            if (testCase == null || testCase.steps() == null) {
                continue;
            }
            for (TestStepSpec step : testCase.steps()) {
                if (step == null || step.selector() == null || step.selector().isBlank()) {
                    continue;
                }
                selectors.add(step.selector().trim());
            }
        }
        return selectors;
    }

    private String selectInteractionSelector(Set<String> selectors) {
        if (selectors == null || selectors.isEmpty()) {
            return null;
        }
        if (selectors.contains("button")) {
            return "button";
        }
        return selectors.stream()
                .filter(selector -> selector.startsWith("#") || selector.startsWith("."))
                .findFirst()
                .orElse(null);
    }

    private TestCaseSpec buildObservedInteractionCase(
            String entry,
            String selector,
            UiRuntimeContract runtimeContract,
            DocumentLanguage language
    ) {
        UiObservationTarget target = observationPolicy.requiredTarget(runtimeContract, CapabilitySurface.PRIMARY_INTERACTION);
        if (target == null) {
            return null;
        }
        List<TestStepSpec> steps = new ArrayList<>();
        steps.add(new TestStepSpec(TestStepAction.ASSERT_SELECTOR, selector, null, null, null, null, false, TestStepSemantic.PRIMARY_CONTROL));
        steps.add(observationPolicy.presenceAssertion(target));
        steps.add(observationPolicy.snapshotStep(target, "interactive-surface", false));
        steps.add(new TestStepSpec(TestStepAction.CLICK, selector, null, null, null, null, false, TestStepSemantic.PRIMARY_CONTROL));
        steps.add(new TestStepSpec(
                TestStepAction.WAIT,
                null,
                null,
                null,
                observationPolicy.observationWaitMs(List.of(CapabilitySurface.PRIMARY_INTERACTION)),
                null,
                false
        ));
        steps.add(observationPolicy.changedAssertion(target, "interactive-surface", false));
        steps.add(new TestStepSpec(TestStepAction.ASSERT_NO_ERRORS, null, null, null, null, null, false));
        return new TestCaseSpec(
                "TC-FUNC-INTERACTIVE-STATE",
                language.choose("交互后状态发生变化", "Interaction causes an observable state change"),
                "functional",
                true,
                entry,
                "",
                language.choose("交互后页面状态必须发生可观察变化，而不只是保持不报错。", "After interaction, the page state must change observably rather than merely avoid errors."),
                List.copyOf(steps),
                List.of(CapabilitySurface.PRIMARY_INTERACTION)
        );
    }
}
