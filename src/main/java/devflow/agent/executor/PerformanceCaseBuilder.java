package devflow.agent.executor;

import devflow.agent.context.ValidationMetadata;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.quality.CapabilityIds;
import java.util.List;

/**
 * 基于结构化性能约束生成基础性能用例。
 */
final class PerformanceCaseBuilder {

    void appendPerformanceCases(
            List<TestCaseSpec> cases,
            String entry,
            RuntimeSnapshot runtimeSnapshot,
            ValidationMetadata validationMetadata,
            DocumentLanguage language
    ) {
        PerformanceRequirements requirements = extractPerformanceRequirements(validationMetadata);
        if (requirements.pageLoadMs() != null) {
            cases.add(new TestCaseSpec(
                    "TC-PERF-LOAD",
                    language.choose("页面加载耗时满足要求", "Page load time meets requirement"),
                    "performance",
                    true,
                    entry,
                    "",
                    language.choose("页面加载耗时应低于要求阈值。", "Page load time should stay under the required threshold."),
                    List.of(
                            new TestStepSpec(TestStepAction.MEASURE_PAGE_LOAD_MAX_MS, null, null, null, requirements.pageLoadMs(), null, false),
                            new TestStepSpec(TestStepAction.ASSERT_NO_ERRORS, null, null, null, null, null, false)
                    ),
                    List.of(CapabilityIds.PAGE_LOAD, CapabilityIds.PERFORMANCE_LOAD, CapabilityIds.RUNTIME_STABILITY)
            ));
        }
        if (requirements.interactionMs() != null
                && runtimeSnapshot != null
                && runtimeSnapshot.exposesMetric(WebRuntimeMetricKeys.LAST_ACTION_MS)) {
            cases.add(new TestCaseSpec(
                    "TC-PERF-RUNTIME",
                    language.choose("运行时指标满足要求", "Runtime metrics meet requirement"),
                    "performance",
                    false,
                    entry,
                    language.choose(
                            "实现需暴露 " + WebRuntimeMetricKeys.metricPath(WebRuntimeMetricKeys.LAST_ACTION_MS) + " 或等价指标。",
                            "The implementation must expose " + WebRuntimeMetricKeys.metricPath(WebRuntimeMetricKeys.LAST_ACTION_MS) + " or an equivalent metric."
                    ),
                    language.choose("运行时关键操作指标应低于要求阈值。", "Critical runtime interaction metrics should stay under the required threshold."),
                    List.of(
                            new TestStepSpec(TestStepAction.ASSERT_WINDOW_METRIC_MAX_MS, null, null, null, requirements.interactionMs(), WebRuntimeMetricKeys.LAST_ACTION_MS, false),
                            new TestStepSpec(TestStepAction.ASSERT_NO_ERRORS, null, null, null, null, null, false)
                    ),
                    List.of(CapabilityIds.PERFORMANCE_INTERACTION, CapabilityIds.RUNTIME_STABILITY)
            ));
        }
    }

    private PerformanceRequirements extractPerformanceRequirements(ValidationMetadata metadata) {
        ValidationMetadata resolved = metadata == null ? ValidationMetadata.empty() : metadata;
        return new PerformanceRequirements(resolved.pageLoadMaxMs(), resolved.interactionMaxMs());
    }

    private record PerformanceRequirements(
            Integer pageLoadMs,
            Integer interactionMs
    ) {
    }
}
