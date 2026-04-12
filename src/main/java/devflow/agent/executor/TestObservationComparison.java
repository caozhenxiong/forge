package devflow.agent.executor;

import devflow.agent.util.EnumParsers;

/**
 * 运行时观测比较方式。
 *
 * <p>核心只关心“怎么比”，不关心 pause/reset/score 这类业务名词。
 */
public enum TestObservationComparison {
    NONE,
    CHANGED,
    UNCHANGED;

    public static TestObservationComparison fromWireValue(String value) {
        return EnumParsers.parseIgnoreCase(TestObservationComparison.class, value, NONE);
    }

    public boolean requiresSnapshotComparison() {
        return this != NONE;
    }
}
