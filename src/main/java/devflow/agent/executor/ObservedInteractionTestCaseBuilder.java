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

    List<TestCaseSpec> strengthenCases(List<TestCaseSpec> cases, RuntimeSnapshot runtimeSnapshot) {
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
                    steps = addObservablePostcondition(steps, runtimeSnapshot, testCase.id());
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
            String selector = selectInteractionSelector(runtimeSnapshot == null ? Set.of() : new LinkedHashSet<>(runtimeSnapshot.selectors()));
            if (selector != null) {
                strengthened.add(buildObservedInteractionCase(
                        cases.getFirst().entry(),
                        selector,
                        runtimeSnapshot != null && runtimeSnapshot.canvasCount() > 0,
                        DocumentLanguage.detect(cases.getFirst().title(), cases.getFirst().expected())
                ));
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

    private List<TestStepSpec> addObservablePostcondition(List<TestStepSpec> steps, RuntimeSnapshot runtimeSnapshot, String caseId) {
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
        String snapshotKey = "observed-" + caseId;
        boolean preferCanvas = runtimeSnapshot != null && runtimeSnapshot.canvasCount() > 0;
        strengthened.add(firstInteractionIndex, preferCanvas
                ? new TestStepSpec(TestStepAction.SNAPSHOT_CANVAS_HASH, "canvas", null, null, null, snapshotKey, false, TestStepSemantic.PRIMARY_SURFACE)
                : new TestStepSpec(TestStepAction.SNAPSHOT_DOM_SIGNATURE, "body", null, null, null, snapshotKey, false, TestStepSemantic.PRIMARY_SURFACE));
        if (strengthened.stream().noneMatch(step -> step.action() == TestStepAction.WAIT
                && step.ms() != null
                && step.ms() >= TestPlanningPolicy.defaultStepWaitMs())) {
            strengthened.add(new TestStepSpec(
                    TestStepAction.WAIT,
                    null,
                    null,
                    null,
                    TestPlanningPolicy.strengthenedInteractionWaitMs(),
                    null,
                    false
            ));
        }
        strengthened.add(preferCanvas
                ? new TestStepSpec(TestStepAction.ASSERT_CANVAS_HASH_CHANGED, "canvas", null, null, null, snapshotKey, false, TestStepSemantic.PRIMARY_SURFACE)
                : new TestStepSpec(TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED, "body", null, null, null, snapshotKey, false, TestStepSemantic.PRIMARY_SURFACE));
        return strengthened;
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
            boolean preferCanvasObservation,
            DocumentLanguage language
    ) {
        List<TestStepSpec> steps = new ArrayList<>();
        steps.add(new TestStepSpec(TestStepAction.ASSERT_SELECTOR, selector, null, null, null, null, false, TestStepSemantic.PRIMARY_CONTROL));
        if (preferCanvasObservation) {
            steps.add(new TestStepSpec(TestStepAction.ASSERT_CANVAS_MIN, null, null, 1, null, null, false, TestStepSemantic.PRIMARY_SURFACE));
            steps.add(new TestStepSpec(TestStepAction.SNAPSHOT_CANVAS_HASH, "canvas", null, null, null, "interactive-surface", false, TestStepSemantic.PRIMARY_SURFACE));
        } else {
            steps.add(new TestStepSpec(TestStepAction.SNAPSHOT_DOM_SIGNATURE, "body", null, null, null, "interactive-surface", false, TestStepSemantic.PRIMARY_SURFACE));
        }
        steps.add(new TestStepSpec(TestStepAction.CLICK, selector, null, null, null, null, false, TestStepSemantic.PRIMARY_CONTROL));
        steps.add(new TestStepSpec(TestStepAction.WAIT, null, null, null, TestPlanningPolicy.observedInteractionWaitMs(), null, false));
        steps.add(preferCanvasObservation
                ? new TestStepSpec(TestStepAction.ASSERT_CANVAS_HASH_CHANGED, "canvas", null, null, null, "interactive-surface", false, TestStepSemantic.PRIMARY_SURFACE)
                : new TestStepSpec(TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED, "body", null, null, null, "interactive-surface", false, TestStepSemantic.PRIMARY_SURFACE));
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
