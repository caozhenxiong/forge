package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import devflow.agent.util.EnumParsers;

/**
 * 测试步骤动作协议。
 *
 * <p>集中维护测试计划、执行器和渲染器共享的动作值，避免在多个模块里反复硬写同一组协议字符串。
 */
public enum TestStepAction {
    ASSERT_SELECTOR,
    ASSERT_CANVAS_MIN,
    CLICK,
    PRESS_KEY,
    WAIT,
    ASSERT_NO_ERRORS,
    ASSERT_TEXT_CONTAINS,
    MEASURE_PAGE_LOAD_MAX_MS,
    ASSERT_WINDOW_METRIC_MAX_MS,
    SNAPSHOT_CANVAS_HASH,
    ASSERT_CANVAS_HASH_CHANGED,
    ASSERT_CANVAS_HASH_UNCHANGED,
    SNAPSHOT_DOM_SIGNATURE,
    ASSERT_DOM_SIGNATURE_CHANGED,
    ASSERT_DOM_SIGNATURE_UNCHANGED;

    private static final Set<TestStepAction> INTERACTIVE_ACTIONS = Set.of(CLICK, PRESS_KEY);
    private static final Set<TestStepAction> OBSERVABLE_POSTCONDITIONS = Set.of(
            ASSERT_CANVAS_HASH_CHANGED,
            ASSERT_CANVAS_HASH_UNCHANGED,
            ASSERT_DOM_SIGNATURE_CHANGED,
            ASSERT_DOM_SIGNATURE_UNCHANGED
    );

    public static TestStepAction fromWireValue(String value) {
        return EnumParsers.parseIgnoreCase(TestStepAction.class, value, null);
    }

    public static String wireCatalog() {
        return Arrays.stream(values())
                .map(TestStepAction::name)
                .collect(Collectors.joining("|"));
    }

    public boolean isInteractive() {
        return INTERACTIVE_ACTIONS.contains(this);
    }

    public boolean isObservablePostcondition() {
        return OBSERVABLE_POSTCONDITIONS.contains(this);
    }
}
