package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

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
            String key = normalize(raw.key());
            if (action == TestStepAction.PRESS_KEY && key.isBlank()) {
                continue;
            }
            result.add(new TestStepSpec(
                    action,
                    normalize(raw.selector()),
                    key,
                    raw.count(),
                    raw.ms(),
                    normalize(raw.text()),
                    raw.optional() != null && raw.optional(),
                    TestStepSemantic.fromWireValue(raw.semantic())
            ));
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
