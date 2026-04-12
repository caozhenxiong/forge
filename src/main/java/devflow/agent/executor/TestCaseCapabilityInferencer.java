package devflow.agent.executor;

import devflow.agent.quality.CapabilityIds;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 基于结构化 testcase 步骤推断能力标签。
 *
 * <p>这里只消费 action / semantic 这类稳定协议字段，不从自然语言 prose 或 selector 文本里猜语义。
 */
final class TestCaseCapabilityInferencer {

    List<String> infer(TestCaseSpec testCase) {
        if (testCase == null || testCase.steps() == null || testCase.steps().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        boolean hasInteractiveAction = false;
        boolean hasObservablePostcondition = false;
        boolean hasPageLoadMeasurement = false;
        for (TestStepSpec step : testCase.steps()) {
            if (step == null || step.action() == null) {
                continue;
            }
            switch (step.action()) {
                case ASSERT_CANVAS_MIN -> capabilities.add(CapabilityIds.PRIMARY_VISUAL_SURFACE);
                case ASSERT_NO_ERRORS -> capabilities.add(CapabilityIds.RUNTIME_STABILITY);
                case MEASURE_PAGE_LOAD_MAX_MS -> {
                    capabilities.add(CapabilityIds.PAGE_LOAD);
                    capabilities.add(CapabilityIds.PERFORMANCE_LOAD);
                    hasPageLoadMeasurement = true;
                }
                case ASSERT_WINDOW_METRIC_MAX_MS -> capabilities.add(CapabilityIds.PERFORMANCE_INTERACTION);
                case CLICK, PRESS_KEY -> hasInteractiveAction = true;
                case ASSERT_CANVAS_HASH_CHANGED,
                        ASSERT_CANVAS_HASH_UNCHANGED,
                        ASSERT_DOM_SIGNATURE_CHANGED,
                        ASSERT_DOM_SIGNATURE_UNCHANGED -> hasObservablePostcondition = true;
                default -> {
                }
            }
        }
        if (hasInteractiveAction) {
            capabilities.add(CapabilityIds.PRIMARY_INTERACTION);
        }
        if (hasObservablePostcondition && hasInteractiveAction) {
            capabilities.add(CapabilityIds.PRIMARY_INTERACTION);
        }
        if (!testCase.observationTargetId().isBlank()) {
            capabilities.add(testCase.observationTargetId());
        }
        if (!hasInteractiveAction && !hasPageLoadMeasurement && capabilities.contains(CapabilityIds.RUNTIME_STABILITY)) {
            capabilities.add(CapabilityIds.PAGE_LOAD);
        }
        return List.copyOf(capabilities);
    }
}
