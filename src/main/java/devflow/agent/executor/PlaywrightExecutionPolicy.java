package devflow.agent.executor;

import java.time.Duration;

/**
 * 统一维护 Playwright 用例执行链的默认超时。
 *
 * <p>这些值属于测试执行层的稳定策略，不应继续散落在执行器内部。
 */
public final class PlaywrightExecutionPolicy {

    private static final Duration DEFAULT_CASE_EXECUTION_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration DEFAULT_SNAPSHOT_TIMEOUT = Duration.ofSeconds(60);
    private static final String CASE_EXECUTION_TIMEOUT_SECONDS_KEY = "devflow.playwright.case-timeout-seconds";
    private static final String SNAPSHOT_TIMEOUT_SECONDS_KEY = "devflow.playwright.snapshot-timeout-seconds";

    private PlaywrightExecutionPolicy() {
    }

    public static Duration caseExecutionTimeout() {
        return Duration.ofSeconds(readPositiveInt(
                CASE_EXECUTION_TIMEOUT_SECONDS_KEY,
                (int) DEFAULT_CASE_EXECUTION_TIMEOUT.getSeconds()
        ));
    }

    public static Duration snapshotTimeout() {
        return Duration.ofSeconds(readPositiveInt(
                SNAPSHOT_TIMEOUT_SECONDS_KEY,
                (int) DEFAULT_SNAPSHOT_TIMEOUT.getSeconds()
        ));
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
}
