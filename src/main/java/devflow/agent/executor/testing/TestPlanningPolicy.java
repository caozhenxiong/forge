package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 测试用例规划阶段的稳定策略参数。
 *
 * <p>这些值不是业务语义，而是 planner 在补充等待/观测动作时依赖的统一策略。
 * 集中到这里，避免测试规划器里继续散落 150/250/300 这类魔法数字。
 */
@ConfigurationProperties(prefix = "devflow.test-planning")
public record TestPlanningPolicy(
        int defaultStepWaitMs,
        int strengthenedInteractionWaitMs,
        int observedInteractionWaitMs,
        int delayedObservationWaitMs,
        double casePlanOutputRatio
) {

    private static final int DEFAULT_STEP_WAIT_MS = 150;
    private static final int DEFAULT_STRENGTHENED_INTERACTION_WAIT_MS = 250;
    private static final int DEFAULT_OBSERVED_INTERACTION_WAIT_MS = 300;
    private static final int DEFAULT_DELAYED_OBSERVATION_WAIT_MS = 1200;
    private static final double DEFAULT_CASE_PLAN_OUTPUT_RATIO = 1.0d;

    public TestPlanningPolicy() {
        this(
                DEFAULT_STEP_WAIT_MS,
                DEFAULT_STRENGTHENED_INTERACTION_WAIT_MS,
                DEFAULT_OBSERVED_INTERACTION_WAIT_MS,
                DEFAULT_DELAYED_OBSERVATION_WAIT_MS,
                DEFAULT_CASE_PLAN_OUTPUT_RATIO
        );
    }

    public TestPlanningPolicy {
        defaultStepWaitMs = normalizePositive(defaultStepWaitMs, DEFAULT_STEP_WAIT_MS);
        strengthenedInteractionWaitMs = normalizePositive(
                strengthenedInteractionWaitMs,
                DEFAULT_STRENGTHENED_INTERACTION_WAIT_MS
        );
        observedInteractionWaitMs = normalizePositive(observedInteractionWaitMs, DEFAULT_OBSERVED_INTERACTION_WAIT_MS);
        delayedObservationWaitMs = normalizePositive(delayedObservationWaitMs, DEFAULT_DELAYED_OBSERVATION_WAIT_MS);
        casePlanOutputRatio = normalizePositive(casePlanOutputRatio, DEFAULT_CASE_PLAN_OUTPUT_RATIO);
    }

    public int observationWaitMs(TestObservationTrigger trigger) {
        if (trigger == null || trigger == TestObservationTrigger.NONE) {
            return observedInteractionWaitMs;
        }
        return trigger == TestObservationTrigger.AFTER_WAIT
                ? delayedObservationWaitMs
                : observedInteractionWaitMs;
    }

    private static int normalizePositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static double normalizePositive(double value, double fallback) {
        return value > 0 ? value : fallback;
    }
}
