package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.util.EnumParsers;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 测试步骤的结构化语义。
 *
 * <p>这层承载“当前步骤在交互闭环里扮演什么角色”，避免 sanitizer / repair /
 * coverage 继续从 selector 文本里猜 start / pause / score 等领域语义。
 */
public enum TestStepSemantic {
    RUN_STATE_ENTRY("run-state-entry"),
    PAUSE_TOGGLE("pause-toggle"),
    STATE_RESET("state-reset"),
    PRIMARY_CONTROL("primary-control"),
    PRIMARY_SURFACE("primary-surface"),
    PROGRESS_SIGNAL("progress-signal");

    private final String wireValue;

    TestStepSemantic(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static String wireCatalog() {
        return Arrays.stream(values())
                .map(TestStepSemantic::wireValue)
                .collect(Collectors.joining("|"));
    }

    public static TestStepSemantic fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        TestStepSemantic direct = EnumParsers.parseIgnoreCase(TestStepSemantic.class, value, null);
        if (direct != null) {
            return direct;
        }
        String normalized = value.trim().toLowerCase();
        for (TestStepSemantic semantic : values()) {
            if (semantic.wireValue.equals(normalized)) {
                return semantic;
            }
        }
        return null;
    }
}
