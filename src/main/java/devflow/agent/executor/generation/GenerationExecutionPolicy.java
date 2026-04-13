package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.time.Duration;

/**
 * 统一维护 generation 内核的默认执行超时策略。
 *
 * <p>这里收敛的是“没有被上层显式覆盖时”的保守默认值，
 * 避免不同生成调用各自写 120 秒、90 秒这类魔法数字。
 */
public final class GenerationExecutionPolicy {

    private static final Duration DEFAULT_ATTEMPT_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(20);

    private GenerationExecutionPolicy() {
    }

    public static Duration defaultAttemptTimeout() {
        return DEFAULT_ATTEMPT_TIMEOUT;
    }

    public static Duration defaultHeartbeatInterval() {
        return DEFAULT_HEARTBEAT_INTERVAL;
    }
}
