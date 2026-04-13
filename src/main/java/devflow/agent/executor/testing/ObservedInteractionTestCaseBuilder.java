package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.quality.CapabilityIds;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责 required 交互用例的可观察后置条件补强。
 *
 * <p>这层把“点击之后必须能观察到状态变化”的策略集中起来，
 * 避免测试规划结果清洗器继续承担 fallback 交互用例构造。
 */
final class ObservedInteractionTestCaseBuilder {

    private final UiRuntimeObservationPolicy observationPolicy = new UiRuntimeObservationPolicy();

    List<TestCaseSpec> strengthenCases(List<TestCaseSpec> cases, RuntimeSnapshot runtimeSnapshot, UiRuntimeContract runtimeContract) {
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
                    testCase.capabilities(),
                    testCase.observationTargetId().isBlank() ? CapabilityIds.PRIMARY_INTERACTION : testCase.observationTargetId(),
                    testCase.observationTrigger() == TestObservationTrigger.NONE
                            ? TestObservationTrigger.AFTER_INTERACTION
                            : testCase.observationTrigger(),
                    testCase.observationComparison() == TestObservationComparison.NONE
                            ? TestObservationComparison.CHANGED
                            : testCase.observationComparison()
            ));
        }
        if (!hasRequiredInteractiveCase) {
            String selector = selectDeterministicInteractionSelector(runtimeSnapshot, runtimeContract);
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
            List<String> capabilities
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
        UiObservationTarget target = observationPolicy.requiredTarget(runtimeContract, CapabilityIds.PRIMARY_INTERACTION);
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
                    observationPolicy.observationWaitMs(TestObservationTrigger.AFTER_INTERACTION),
                    null,
                    false
            ));
        }
        strengthened.add(observationPolicy.comparisonAssertion(target, TestObservationComparison.CHANGED, snapshotKey, false));
        return strengthened;
    }

    private String selectDeterministicInteractionSelector(RuntimeSnapshot runtimeSnapshot, UiRuntimeContract runtimeContract) {
        if (runtimeContract != null && runtimeContract.runStateEntryTargets().size() == 1) {
            return runtimeContract.runStateEntryTargets().getFirst();
        }
        if (runtimeSnapshot == null || runtimeSnapshot.controlSelectors().size() != 1) {
            return null;
        }
        return runtimeSnapshot.controlSelectors().getFirst();
    }

    private TestCaseSpec buildObservedInteractionCase(
            String entry,
            String selector,
            UiRuntimeContract runtimeContract,
            DocumentLanguage language
    ) {
        UiObservationTarget target = observationPolicy.requiredTarget(runtimeContract, CapabilityIds.PRIMARY_INTERACTION);
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
                observationPolicy.observationWaitMs(TestObservationTrigger.AFTER_INTERACTION),
                null,
                false
        ));
        steps.add(observationPolicy.comparisonAssertion(target, TestObservationComparison.CHANGED, "interactive-surface", false));
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
                List.of(CapabilityIds.PRIMARY_INTERACTION),
                CapabilityIds.PRIMARY_INTERACTION,
                TestObservationTrigger.AFTER_INTERACTION,
                TestObservationComparison.CHANGED
        );
    }
}
