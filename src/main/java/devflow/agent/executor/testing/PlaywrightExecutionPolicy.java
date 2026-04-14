package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一维护 Playwright 用例执行链的默认超时。
 *
 * <p>这些值属于测试执行层的稳定策略，不应继续散落在执行器内部。
 */
@ConfigurationProperties(prefix = "devflow.playwright")
public record PlaywrightExecutionPolicy(
        int caseTimeoutSeconds,
        int snapshotTimeoutSeconds
) {

    private static final int DEFAULT_CASE_EXECUTION_TIMEOUT_SECONDS = 90;
    private static final int DEFAULT_SNAPSHOT_TIMEOUT_SECONDS = 60;

    public PlaywrightExecutionPolicy() {
        this(DEFAULT_CASE_EXECUTION_TIMEOUT_SECONDS, DEFAULT_SNAPSHOT_TIMEOUT_SECONDS);
    }

    public PlaywrightExecutionPolicy {
        caseTimeoutSeconds = normalizePositive(caseTimeoutSeconds, DEFAULT_CASE_EXECUTION_TIMEOUT_SECONDS);
        snapshotTimeoutSeconds = normalizePositive(snapshotTimeoutSeconds, DEFAULT_SNAPSHOT_TIMEOUT_SECONDS);
    }

    public Duration caseExecutionTimeout() {
        return Duration.ofSeconds(caseTimeoutSeconds);
    }

    public Duration snapshotTimeout() {
        return Duration.ofSeconds(snapshotTimeoutSeconds);
    }

    private static int normalizePositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
