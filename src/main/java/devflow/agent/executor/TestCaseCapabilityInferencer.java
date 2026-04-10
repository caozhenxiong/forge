package devflow.agent.executor;

import devflow.agent.quality.CapabilitySurface;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 基于结构化 testcase 步骤推断能力标签。
 *
 * <p>这里只消费 action / semantic 这类稳定协议字段，不从自然语言 prose 或 selector 文本里猜语义。
 */
final class TestCaseCapabilityInferencer {

    List<CapabilitySurface> infer(TestCaseSpec testCase) {
        if (testCase == null || testCase.steps() == null || testCase.steps().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<CapabilitySurface> capabilities = new LinkedHashSet<>();
        boolean hasInteractiveAction = false;
        boolean hasObservablePostcondition = false;
        boolean hasPageLoadMeasurement = false;
        for (TestStepSpec step : testCase.steps()) {
            if (step == null || step.action() == null) {
                continue;
            }
            switch (step.action()) {
                case ASSERT_CANVAS_MIN -> capabilities.add(CapabilitySurface.PRIMARY_VISUAL_SURFACE);
                case ASSERT_NO_ERRORS -> capabilities.add(CapabilitySurface.RUNTIME_STABILITY);
                case MEASURE_PAGE_LOAD_MAX_MS -> {
                    capabilities.add(CapabilitySurface.PAGE_LOAD);
                    capabilities.add(CapabilitySurface.PERFORMANCE_LOAD);
                    hasPageLoadMeasurement = true;
                }
                case ASSERT_WINDOW_METRIC_MAX_MS -> capabilities.add(CapabilitySurface.PERFORMANCE_INTERACTION);
                case CLICK, PRESS_KEY -> hasInteractiveAction = true;
                case ASSERT_CANVAS_HASH_CHANGED, ASSERT_DOM_SIGNATURE_CHANGED -> hasObservablePostcondition = true;
                default -> {
                }
            }
            inferStepSemantic(step.semantic(), capabilities);
        }
        if (hasInteractiveAction) {
            capabilities.add(CapabilitySurface.PRIMARY_INTERACTION);
        }
        if (hasObservablePostcondition && hasInteractiveAction) {
            capabilities.add(CapabilitySurface.PRIMARY_INTERACTION);
        }
        if (!hasInteractiveAction && !hasPageLoadMeasurement && capabilities.contains(CapabilitySurface.RUNTIME_STABILITY)) {
            capabilities.add(CapabilitySurface.PAGE_LOAD);
        }
        return List.copyOf(capabilities);
    }

    private void inferStepSemantic(TestStepSemantic semantic, LinkedHashSet<CapabilitySurface> capabilities) {
        if (semantic == null) {
            return;
        }
        switch (semantic) {
            case PAUSE_TOGGLE -> capabilities.add(CapabilitySurface.PAUSE_FREEZE);
            case STATE_RESET -> capabilities.add(CapabilitySurface.RESET_RESTORES_INITIAL_STATE);
            case PROGRESS_SIGNAL -> capabilities.add(CapabilitySurface.VISIBLE_PROGRESS_SIGNAL);
            case PRIMARY_SURFACE -> capabilities.add(CapabilitySurface.PRIMARY_VISUAL_SURFACE);
            default -> {
            }
        }
    }
}
