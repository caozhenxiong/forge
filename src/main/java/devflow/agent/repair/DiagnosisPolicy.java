package devflow.agent.repair;

import devflow.agent.review.FixMode;

/**
 * 诊断链的稳定策略常量。
 *
 * <p>这里集中的是：
 * 1. 连续失败多少次后触发诊断；
 * 2. 不同修复模式下的阈值选择。
 */
public final class DiagnosisPolicy {

    public static final int DEFAULT_DIAGNOSIS_THRESHOLD = 3;
    public static final int REWORK_DIAGNOSIS_THRESHOLD = 2;

    private DiagnosisPolicy() {
    }

    public static int thresholdFor(FixMode requestedMode) {
        return requestedMode == FixMode.REWORK ? REWORK_DIAGNOSIS_THRESHOLD : DEFAULT_DIAGNOSIS_THRESHOLD;
    }
}
