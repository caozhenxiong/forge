package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.time.Duration;

/**
 * 统一维护 implementation 子任务 review 的执行策略。
 *
 * <p>子任务 review 是结构化、小而频繁的验证调用，
 * 需要和阶段文档 review 区分开，避免预算过小导致反复 `done_reason=length`。
 */
public final class SubtaskReviewPolicy {

    private static final int DEFAULT_MAX_ATTEMPTS = 2;
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(20);
    private static final Duration DEFAULT_ATTEMPT_TIMEOUT = Duration.ofSeconds(90);
    private static final String MAX_ATTEMPTS_KEY = "devflow.subtask-review.max-attempts";
    private static final String HEARTBEAT_INTERVAL_SECONDS_KEY = "devflow.subtask-review.heartbeat-seconds";
    private static final String ATTEMPT_TIMEOUT_SECONDS_KEY = "devflow.subtask-review.attempt-timeout-seconds";

    private SubtaskReviewPolicy() {
    }

    public static int maxAttempts() {
        return readPositiveInt(MAX_ATTEMPTS_KEY, DEFAULT_MAX_ATTEMPTS);
    }

    public static Duration heartbeatInterval() {
        return Duration.ofSeconds(readPositiveInt(
                HEARTBEAT_INTERVAL_SECONDS_KEY,
                (int) DEFAULT_HEARTBEAT_INTERVAL.getSeconds()
        ));
    }

    public static Duration attemptTimeout() {
        return Duration.ofSeconds(readPositiveInt(
                ATTEMPT_TIMEOUT_SECONDS_KEY,
                (int) DEFAULT_ATTEMPT_TIMEOUT.getSeconds()
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
