package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一维护 implementation 子任务 review 的执行策略。
 *
 * <p>子任务 review 是结构化、小而频繁的验证调用，
 * 需要和阶段文档 review 区分开，避免预算过小导致反复 `done_reason=length`。
 */
@ConfigurationProperties(prefix = "devflow.subtask-review")
public record SubtaskReviewPolicy(
        int maxAttempts,
        int heartbeatSeconds,
        int attemptTimeoutSeconds
) {

    private static final int DEFAULT_MAX_ATTEMPTS = 2;
    private static final int DEFAULT_HEARTBEAT_SECONDS = 20;
    private static final int DEFAULT_ATTEMPT_TIMEOUT_SECONDS = 90;

    public SubtaskReviewPolicy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_HEARTBEAT_SECONDS, DEFAULT_ATTEMPT_TIMEOUT_SECONDS);
    }

    public SubtaskReviewPolicy {
        maxAttempts = normalizePositive(maxAttempts, DEFAULT_MAX_ATTEMPTS);
        heartbeatSeconds = normalizePositive(heartbeatSeconds, DEFAULT_HEARTBEAT_SECONDS);
        attemptTimeoutSeconds = normalizePositive(attemptTimeoutSeconds, DEFAULT_ATTEMPT_TIMEOUT_SECONDS);
    }

    public Duration heartbeatInterval() {
        return Duration.ofSeconds(heartbeatSeconds);
    }

    public Duration attemptTimeout() {
        return Duration.ofSeconds(attemptTimeoutSeconds);
    }

    private static int normalizePositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
