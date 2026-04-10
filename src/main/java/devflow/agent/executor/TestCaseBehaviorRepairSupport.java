package devflow.agent.executor;

import devflow.agent.quality.CapabilitySurface;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 修复“结构合法但行为容易误判”的测试用例。
 *
 * <p>这层不重新生成测试用例，而是对已结构化的 case 做最小修复：
 * 1. 交互/观测顺序修复：把 snapshot 放到对应交互前；
 * 2. 运行前置条件注入：按结构化 semantic 注入运行态入口；
 * 3. 脆弱文本断言降级：对 control/progress 相关文本断言做最小降级。
 *
 * <p>注意：这里不再根据 selector 文本猜 start / pause / score 等语义；
 * 这类语义只能来自 step semantic 或 case capability。
 */
final class TestCaseBehaviorRepairSupport {

    List<TestCaseSpec> repairCases(List<TestCaseSpec> cases, RuntimeSnapshot runtimeSnapshot) {
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }
        SemanticSelectorCatalog selectorCatalog = SemanticSelectorCatalog.fromCases(cases);
        List<TestCaseSpec> repaired = new ArrayList<>();
        for (TestCaseSpec testCase : cases) {
            repaired.add(repairCase(testCase, runtimeSnapshot, selectorCatalog));
        }
        return List.copyOf(repaired);
    }

    private TestCaseSpec repairCase(
            TestCaseSpec testCase,
            RuntimeSnapshot runtimeSnapshot,
            SemanticSelectorCatalog selectorCatalog
    ) {
        List<TestStepSpec> steps = new ArrayList<>(testCase.steps() == null ? List.of() : testCase.steps());
        normalizeObservationSurface(steps, runtimeSnapshot);
        repairKeyboardSetup(steps);
        injectRunningStartPreconditions(steps, testCase, selectorCatalog);
        repairObservableSequences(steps, runtimeSnapshot);
        softenBrittleScoreAssertions(steps);
        softenBrittleControlAssertions(steps);
        return new TestCaseSpec(
                testCase.id(),
                testCase.title(),
                testCase.type(),
                testCase.required(),
                testCase.entry(),
                testCase.preconditions(),
                testCase.expected(),
                List.copyOf(steps),
                testCase.capabilities()
        );
    }

    /**
     * 方向键控制用例在首个按键前，只允许保留运行态入口或主观测面 setup。
     *
     * <p>这层不再靠 selector 文本去猜 start/pause，而是只消费结构化 semantic。
     */
    private void repairKeyboardSetup(List<TestStepSpec> steps) {
        int firstKeyIndex = firstKeyIndex(steps);
        if (firstKeyIndex < 0) {
            return;
        }
        Set<String> removedSelectors = new LinkedHashSet<>();
        for (int index = firstKeyIndex - 1; index >= 0; index--) {
            TestStepSpec step = steps.get(index);
            if (step.action() == TestStepAction.CLICK && !isAllowedPreKeyClick(step)) {
                removedSelectors.add(blank(step.selector()));
                steps.remove(index);
                firstKeyIndex--;
                continue;
            }
            if (step.action() == TestStepAction.ASSERT_SELECTOR
                    && (!isAllowedPreKeyAssertion(step) || removedSelectors.contains(blank(step.selector())))) {
                steps.remove(index);
                firstKeyIndex--;
            }
        }
    }

    private void injectRunningStartPreconditions(
            List<TestStepSpec> steps,
            TestCaseSpec testCase,
            SemanticSelectorCatalog selectorCatalog
    ) {
        if (steps.isEmpty() || !requiresRunningState(testCase, steps)) {
            return;
        }
        int firstRunningActionIndex = firstRunningActionIndex(steps);
        if (firstRunningActionIndex < 0) {
            return;
        }
        if (hasRunStateEntryBeforeIndex(steps, firstRunningActionIndex)) {
            return;
        }
        String runStateEntrySelector = firstSelectorForSemantic(steps, TestStepSemantic.RUN_STATE_ENTRY);
        if (runStateEntrySelector.isBlank()) {
            runStateEntrySelector = selectorCatalog.runStateEntrySelector();
        }
        if (runStateEntrySelector.isBlank()) {
            return;
        }
        int insertIndex = firstSetupBoundary(steps);
        relocateRunStateEntryIfPresent(steps, runStateEntrySelector, firstRunningActionIndex, insertIndex);
        if (hasRunStateEntryBeforeIndex(steps, firstRunningActionIndex)) {
            return;
        }
        List<TestStepSpec> injected = new ArrayList<>();
        if (!hasSemanticAssertionBeforeIndex(steps, runStateEntrySelector, TestStepSemantic.RUN_STATE_ENTRY, insertIndex)) {
            injected.add(new TestStepSpec(
                    TestStepAction.ASSERT_SELECTOR,
                    runStateEntrySelector,
                    null,
                    null,
                    null,
                    null,
                    false,
                    TestStepSemantic.RUN_STATE_ENTRY
            ));
        }
        injected.add(new TestStepSpec(
                TestStepAction.CLICK,
                runStateEntrySelector,
                null,
                null,
                null,
                null,
                false,
                TestStepSemantic.RUN_STATE_ENTRY
        ));
        injected.add(new TestStepSpec(
                TestStepAction.WAIT,
                null,
                null,
                null,
                TestPlanningPolicy.strengthenedInteractionWaitMs(),
                null,
                false
        ));
        steps.addAll(insertIndex, injected);
    }

    private void repairObservableSequences(List<TestStepSpec> steps, RuntimeSnapshot runtimeSnapshot) {
        for (int index = 0; index < steps.size(); index++) {
            TestStepAction action = steps.get(index).action();
            if (action == null || !action.isObservablePostcondition()) {
                continue;
            }
            repairObservableSequence(steps, runtimeSnapshot, action, index);
        }
    }

    private void normalizeObservationSurface(List<TestStepSpec> steps, RuntimeSnapshot runtimeSnapshot) {
        if (runtimeSnapshot == null || runtimeSnapshot.canvasCount() > 0 || steps.isEmpty()) {
            return;
        }
        String domSelector = preferredDomObservationSelector(runtimeSnapshot);
        for (int index = 0; index < steps.size(); index++) {
            TestStepSpec step = steps.get(index);
            if (step.action() == TestStepAction.SNAPSHOT_CANVAS_HASH) {
                steps.set(index, new TestStepSpec(
                        TestStepAction.SNAPSHOT_DOM_SIGNATURE,
                        domSelector,
                        null,
                        null,
                        null,
                        blank(step.text()),
                        step.optional(),
                        TestStepSemantic.PRIMARY_SURFACE
                ));
                continue;
            }
            if (step.action() == TestStepAction.ASSERT_CANVAS_HASH_CHANGED) {
                steps.set(index, new TestStepSpec(
                        TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED,
                        domSelector,
                        null,
                        null,
                        null,
                        blank(step.text()),
                        step.optional(),
                        TestStepSemantic.PRIMARY_SURFACE
                ));
                continue;
            }
            if (step.action() == TestStepAction.ASSERT_CANVAS_MIN) {
                steps.set(index, new TestStepSpec(
                        TestStepAction.ASSERT_SELECTOR,
                        domSelector,
                        null,
                        null,
                        null,
                        null,
                        step.optional(),
                        TestStepSemantic.PRIMARY_SURFACE
                ));
            }
        }
    }

    /**
     * 启动/暂停按钮的精确文案容易随实现或时序变化而抖动。
     *
     * <p>这里不靠 selector 文本判断 control，而只看结构化 semantic。
     */
    private void softenBrittleControlAssertions(List<TestStepSpec> steps) {
        if (steps.isEmpty()) {
            return;
        }
        int controlClickCount = 0;
        int controlTextAssertionCount = 0;
        for (TestStepSpec step : steps) {
            if (step.action() == TestStepAction.CLICK && isControlStep(step)) {
                controlClickCount++;
            }
            if (step.action() == TestStepAction.ASSERT_TEXT_CONTAINS && isControlStep(step)) {
                controlTextAssertionCount++;
            }
        }
        if (controlTextAssertionCount == 0) {
            return;
        }
        if (controlClickCount <= 1) {
            softenSingleControlActivationObservations(steps);
            return;
        }
        for (int index = 0; index < steps.size(); index++) {
            TestStepSpec step = steps.get(index);
            if (step.action() == TestStepAction.ASSERT_TEXT_CONTAINS && isControlStep(step)) {
                steps.set(index, optional(step));
            }
        }
    }

    private void softenSingleControlActivationObservations(List<TestStepSpec> steps) {
        boolean hasControlTextAssertion = steps.stream().anyMatch(step ->
                step.action() == TestStepAction.ASSERT_TEXT_CONTAINS && isControlStep(step));
        if (!hasControlTextAssertion) {
            return;
        }
        for (int index = 0; index < steps.size(); index++) {
            TestStepSpec step = steps.get(index);
            if (step.action() == TestStepAction.ASSERT_CANVAS_HASH_CHANGED
                    || step.action() == TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED) {
                steps.set(index, optional(step));
            }
        }
    }

    private void repairObservableSequence(
            List<TestStepSpec> steps,
            RuntimeSnapshot runtimeSnapshot,
            TestStepAction assertAction,
            int assertIndex
    ) {
        int interactiveIndex = findPreviousInteractiveIndex(steps, assertIndex);
        if (interactiveIndex < 0) {
            return;
        }
        TestStepAction snapshotAction = snapshotActionFor(assertAction);
        if (snapshotAction == null) {
            return;
        }
        int snapshotIndex = findPreviousActionIndex(steps, assertIndex, snapshotAction);
        if (snapshotIndex < 0) {
            steps.add(interactiveIndex, buildSnapshotStep(assertAction, steps.get(assertIndex), runtimeSnapshot));
            assertIndex++;
        } else if (snapshotIndex > interactiveIndex) {
            TestStepSpec snapshotStep = steps.remove(snapshotIndex);
            steps.add(interactiveIndex, snapshotStep);
            if (snapshotIndex < assertIndex) {
                assertIndex--;
            }
            assertIndex = indexOfReference(steps, steps.get(assertIndex));
        }
        ensureWaitBetweenInteractionAndAssertion(steps, assertAction, assertIndex);
    }

    private void softenBrittleScoreAssertions(List<TestStepSpec> steps) {
        if (steps.isEmpty()) {
            return;
        }
        Set<String> removedProgressSelectors = new LinkedHashSet<>();
        steps.removeIf(step -> {
            if (step.action() != TestStepAction.ASSERT_TEXT_CONTAINS || step.semantic() != TestStepSemantic.PROGRESS_SIGNAL) {
                return false;
            }
            if (!isPureNumericText(step.text())) {
                return false;
            }
            removedProgressSelectors.add(step.selector());
            return true;
        });
        for (String selector : removedProgressSelectors) {
            if (blank(selector).isBlank()) {
                continue;
            }
            if (steps.stream().noneMatch(step -> step.action() == TestStepAction.ASSERT_SELECTOR && selector.equals(step.selector()))) {
                steps.add(firstSetupBoundary(steps), new TestStepSpec(
                        TestStepAction.ASSERT_SELECTOR,
                        selector,
                        null,
                        null,
                        null,
                        null,
                        false,
                        TestStepSemantic.PROGRESS_SIGNAL
                ));
            }
        }
    }

    private void ensureWaitBetweenInteractionAndAssertion(List<TestStepSpec> steps, TestStepAction assertAction, int assertIndex) {
        int interactiveIndex = findPreviousInteractiveIndex(steps, assertIndex);
        if (interactiveIndex < 0) {
            return;
        }
        boolean hasWait = false;
        for (int index = interactiveIndex + 1; index < assertIndex; index++) {
            if (steps.get(index).action() == TestStepAction.WAIT) {
                hasWait = true;
                break;
            }
        }
        if (!hasWait) {
            steps.add(assertIndex, new TestStepSpec(
                    TestStepAction.WAIT,
                    null,
                    null,
                    null,
                    TestPlanningPolicy.strengthenedInteractionWaitMs(),
                    null,
                    false
            ));
        }
    }

    private TestStepSpec buildSnapshotStep(
            TestStepAction assertAction,
            TestStepSpec assertStep,
            RuntimeSnapshot runtimeSnapshot
    ) {
        if (assertAction == TestStepAction.ASSERT_CANVAS_HASH_CHANGED) {
            String selector = blank(assertStep.selector()).isBlank() ? defaultCanvasSelector() : assertStep.selector();
            return new TestStepSpec(
                    TestStepAction.SNAPSHOT_CANVAS_HASH,
                    selector,
                    null,
                    null,
                    null,
                    blank(assertStep.text()),
                    false,
                    TestStepSemantic.PRIMARY_SURFACE
            );
        }
        String selector = blank(assertStep.selector()).isBlank() ? preferredDomObservationSelector(runtimeSnapshot) : assertStep.selector();
        return new TestStepSpec(
                TestStepAction.SNAPSHOT_DOM_SIGNATURE,
                selector,
                null,
                null,
                null,
                blank(assertStep.text()),
                false,
                TestStepSemantic.PRIMARY_SURFACE
        );
    }

    private String defaultCanvasSelector() {
        return "canvas";
    }

    private String preferredDomObservationSelector(RuntimeSnapshot runtimeSnapshot) {
        if (runtimeSnapshot == null || runtimeSnapshot.selectors() == null || runtimeSnapshot.selectors().isEmpty()) {
            return "body";
        }
        return runtimeSnapshot.selectors().stream()
                .filter(selector -> selector != null && !selector.isBlank())
                .filter(selector -> selector.startsWith("#") || selector.startsWith("."))
                .findFirst()
                .orElse("body");
    }

    private boolean requiresRunningState(TestCaseSpec testCase, List<TestStepSpec> steps) {
        if (steps.stream().anyMatch(step -> step.action() == TestStepAction.PRESS_KEY)) {
            return true;
        }
        if (testCase == null || testCase.capabilities() == null || testCase.capabilities().isEmpty()) {
            return false;
        }
        return testCase.capabilities().contains(CapabilitySurface.PAUSE_FREEZE)
                || testCase.capabilities().contains(CapabilitySurface.TIMED_STATE_PROGRESSION);
    }

    private int firstRunningActionIndex(List<TestStepSpec> steps) {
        for (int index = 0; index < steps.size(); index++) {
            TestStepSpec step = steps.get(index);
            if (step.action() == TestStepAction.PRESS_KEY
                    || (step.action() == TestStepAction.CLICK && step.semantic() == TestStepSemantic.PAUSE_TOGGLE)) {
                return index;
            }
        }
        return -1;
    }

    private int firstSetupBoundary(List<TestStepSpec> steps) {
        int index = 0;
        while (index < steps.size()) {
            TestStepAction action = steps.get(index).action();
            if (action == TestStepAction.ASSERT_SELECTOR
                    || action == TestStepAction.ASSERT_CANVAS_MIN
                    || action == TestStepAction.ASSERT_NO_ERRORS
                    || action == TestStepAction.MEASURE_PAGE_LOAD_MAX_MS
                    || action == TestStepAction.ASSERT_WINDOW_METRIC_MAX_MS) {
                index++;
                continue;
            }
            break;
        }
        return index;
    }

    private boolean hasRunStateEntryBeforeIndex(List<TestStepSpec> steps, int indexExclusive) {
        for (int index = 0; index < indexExclusive; index++) {
            TestStepSpec step = steps.get(index);
            if (step.action() == TestStepAction.CLICK && step.semantic() == TestStepSemantic.RUN_STATE_ENTRY) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSemanticAssertionBeforeIndex(
            List<TestStepSpec> steps,
            String selector,
            TestStepSemantic semantic,
            int indexExclusive
    ) {
        for (int index = 0; index < indexExclusive; index++) {
            TestStepSpec step = steps.get(index);
            if (step.action() == TestStepAction.ASSERT_SELECTOR
                    && selector.equals(step.selector())
                    && step.semantic() == semantic) {
                return true;
            }
        }
        return false;
    }

    private int findPreviousInteractiveIndex(List<TestStepSpec> steps, int beforeIndex) {
        for (int index = beforeIndex - 1; index >= 0; index--) {
            if (steps.get(index).action() != null && steps.get(index).action().isInteractive()) {
                return index;
            }
        }
        return -1;
    }

    private int findPreviousActionIndex(List<TestStepSpec> steps, int beforeIndex, TestStepAction action) {
        for (int index = beforeIndex - 1; index >= 0; index--) {
            if (steps.get(index).action() == action) {
                return index;
            }
        }
        return -1;
    }

    private int indexOfReference(List<TestStepSpec> steps, TestStepSpec reference) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index) == reference) {
                return index;
            }
        }
        return steps.indexOf(reference);
    }

    private int firstKeyIndex(List<TestStepSpec> steps) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).action() == TestStepAction.PRESS_KEY) {
                return index;
            }
        }
        return -1;
    }

    private TestStepAction snapshotActionFor(TestStepAction assertAction) {
        if (assertAction == TestStepAction.ASSERT_CANVAS_HASH_CHANGED) {
            return TestStepAction.SNAPSHOT_CANVAS_HASH;
        }
        if (assertAction == TestStepAction.ASSERT_DOM_SIGNATURE_CHANGED) {
            return TestStepAction.SNAPSHOT_DOM_SIGNATURE;
        }
        return null;
    }

    private TestStepSpec optional(TestStepSpec step) {
        return new TestStepSpec(
                step.action(),
                step.selector(),
                step.key(),
                step.count(),
                step.ms(),
                step.text(),
                true,
                step.semantic()
        );
    }

    private boolean isAllowedPreKeyClick(TestStepSpec step) {
        return step != null && step.semantic() == TestStepSemantic.RUN_STATE_ENTRY;
    }

    private boolean isAllowedPreKeyAssertion(TestStepSpec step) {
        if (step == null) {
            return false;
        }
        return step.semantic() == TestStepSemantic.RUN_STATE_ENTRY
                || step.semantic() == TestStepSemantic.PRIMARY_SURFACE;
    }

    private String firstSelectorForSemantic(List<TestStepSpec> steps, TestStepSemantic semantic) {
        if (steps == null || steps.isEmpty() || semantic == null) {
            return "";
        }
        for (TestStepSpec step : steps) {
            if (step != null && step.semantic() == semantic && !blank(step.selector()).isBlank()) {
                return step.selector();
            }
        }
        return "";
    }

    private void relocateRunStateEntryIfPresent(
            List<TestStepSpec> steps,
            String selector,
            int firstRunningActionIndex,
            int insertIndex
    ) {
        int clickIndex = -1;
        int assertIndex = -1;
        for (int index = 0; index < steps.size(); index++) {
            TestStepSpec step = steps.get(index);
            if (step.action() == TestStepAction.CLICK
                    && step.semantic() == TestStepSemantic.RUN_STATE_ENTRY
                    && selector.equals(step.selector())) {
                clickIndex = index;
                break;
            }
        }
        if (clickIndex < 0 || clickIndex < firstRunningActionIndex) {
            return;
        }
        if (clickIndex > 0) {
            TestStepSpec previous = steps.get(clickIndex - 1);
            if (previous.action() == TestStepAction.ASSERT_SELECTOR
                    && previous.semantic() == TestStepSemantic.RUN_STATE_ENTRY
                    && selector.equals(previous.selector())) {
                assertIndex = clickIndex - 1;
            }
        }
        TestStepSpec clickStep = steps.remove(clickIndex);
        if (assertIndex >= 0) {
            TestStepSpec assertStep = steps.remove(assertIndex);
            if (assertIndex < insertIndex) {
                insertIndex--;
            }
            steps.add(insertIndex, assertStep);
            insertIndex++;
        } else if (clickIndex < insertIndex) {
            insertIndex--;
        }
        steps.add(insertIndex, clickStep);
    }

    private boolean isControlStep(TestStepSpec step) {
        return step != null
                && (step.semantic() == TestStepSemantic.RUN_STATE_ENTRY
                || step.semantic() == TestStepSemantic.PAUSE_TOGGLE);
    }

    private boolean isPureNumericText(String text) {
        String normalized = blank(text).trim();
        if (normalized.isBlank()) {
            return false;
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (!Character.isDigit(normalized.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private record SemanticSelectorCatalog(String runStateEntrySelector) {

        static SemanticSelectorCatalog fromCases(List<TestCaseSpec> cases) {
            if (cases == null || cases.isEmpty()) {
                return new SemanticSelectorCatalog("");
            }
            for (TestCaseSpec testCase : cases) {
                if (testCase == null || testCase.steps() == null) {
                    continue;
                }
                for (TestStepSpec step : testCase.steps()) {
                    if (step != null
                            && step.semantic() == TestStepSemantic.RUN_STATE_ENTRY
                            && !blankValue(step.selector()).isBlank()) {
                        return new SemanticSelectorCatalog(step.selector());
                    }
                }
            }
            return new SemanticSelectorCatalog("");
        }

        private static String blankValue(String value) {
            return value == null ? "" : value;
        }
    }
}
