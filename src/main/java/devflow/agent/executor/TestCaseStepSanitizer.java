package devflow.agent.executor;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责把模型返回的 step payload 清洗成稳定的 `TestStepSpec`。
 *
 * <p>这层只做 step 级结构化清洗，不处理基础计划构造和交互增强。
 */
final class TestCaseStepSanitizer {

    List<TestStepSpec> sanitizeSteps(List<PlannedTestStepPayload> rawSteps) {
        if (rawSteps == null || rawSteps.isEmpty()) {
            return List.of();
        }
        List<TestStepSpec> result = new ArrayList<>();
        for (PlannedTestStepPayload raw : rawSteps) {
            if (raw == null || raw.action() == null || raw.action().isBlank()) {
                continue;
            }
            TestStepAction action = TestStepAction.fromWireValue(raw.action());
            if (action == null) {
                continue;
            }
            result.add(new TestStepSpec(
                    action,
                    blank(raw.selector()),
                    blank(raw.key()),
                    raw.count(),
                    raw.ms(),
                    blank(raw.text()),
                    raw.optional() != null && raw.optional(),
                    TestStepSemantic.fromWireValue(raw.semantic())
            ));
        }
        return result;
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
