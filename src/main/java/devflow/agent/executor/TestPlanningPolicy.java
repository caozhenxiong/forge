package devflow.agent.executor;

/**
 * 测试用例规划阶段的稳定策略参数。
 *
 * <p>这些值不是业务语义，而是 planner 在补充等待/观测动作时依赖的统一策略。
 * 集中到这里，避免测试规划器里继续散落 150/250/300 这类魔法数字。
 */
public final class TestPlanningPolicy {

    private static final int DEFAULT_STEP_WAIT_MS = 150;
    private static final int DEFAULT_STRENGTHENED_INTERACTION_WAIT_MS = 250;
    private static final int DEFAULT_OBSERVED_INTERACTION_WAIT_MS = 300;
    private static final double DEFAULT_CASE_PLAN_OUTPUT_RATIO = 1.0d;
    private static final String DEFAULT_STEP_WAIT_MS_KEY = "devflow.test-planning.default-step-wait-ms";
    private static final String STRENGTHENED_INTERACTION_WAIT_MS_KEY = "devflow.test-planning.strengthened-interaction-wait-ms";
    private static final String OBSERVED_INTERACTION_WAIT_MS_KEY = "devflow.test-planning.observed-interaction-wait-ms";
    private static final String CASE_PLAN_OUTPUT_RATIO_KEY = "devflow.test-planning.case-plan-output-ratio";

    private TestPlanningPolicy() {
    }

    public static int defaultStepWaitMs() {
        return readPositiveInt(DEFAULT_STEP_WAIT_MS_KEY, DEFAULT_STEP_WAIT_MS);
    }

    public static int strengthenedInteractionWaitMs() {
        return readPositiveInt(
                STRENGTHENED_INTERACTION_WAIT_MS_KEY,
                DEFAULT_STRENGTHENED_INTERACTION_WAIT_MS
        );
    }

    public static int observedInteractionWaitMs() {
        return readPositiveInt(
                OBSERVED_INTERACTION_WAIT_MS_KEY,
                DEFAULT_OBSERVED_INTERACTION_WAIT_MS
        );
    }

    public static double casePlanOutputRatio() {
        return readPositiveDouble(CASE_PLAN_OUTPUT_RATIO_KEY, DEFAULT_CASE_PLAN_OUTPUT_RATIO);
    }

    private static int readPositiveInt(String key, int fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double readPositiveDouble(String key, double fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
