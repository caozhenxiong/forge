package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.util.EnumParsers;

/**
 * 运行时观测的触发方式。
 *
 * <p>这里表达的是测试机制，而不是具体业务语义：
 * 1. `AFTER_INTERACTION` 表示交互后观察；
 * 2. `AFTER_WAIT` 表示等待一段时间后观察；
 * 3. `NONE` 表示没有状态比较。
 */
public enum TestObservationTrigger {
    NONE,
    AFTER_INTERACTION,
    AFTER_WAIT;

    public static TestObservationTrigger fromWireValue(String value) {
        return EnumParsers.parseIgnoreCase(TestObservationTrigger.class, value, NONE);
    }

    public boolean requiresComparisonWindow() {
        return this != NONE;
    }
}
