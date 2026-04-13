package devflow.agent.orchestrator;

import devflow.agent.domain.StageType;

import java.time.Duration;

/**
 * 集中维护阶段级长调用的 heartbeat 与超时策略。
 *
 * <p>文档阶段的生成通常是单次较短调用；实现、代码审阅和测试阶段则可能
 * 包含多轮子任务、局部编辑和校验，不能再沿用同一条绝对超时。
 */
public final class StageOperationPolicy {

    private static final Duration DEFAULT_STAGE_HEARTBEAT_INTERVAL = Duration.ofSeconds(20);
    private static final Duration DEFAULT_DOCUMENT_STAGE_GENERATION_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DEFAULT_EXECUTION_STAGE_GENERATION_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration DEFAULT_DOCUMENT_STAGE_REVIEW_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration DEFAULT_EXECUTION_STAGE_REVIEW_TIMEOUT = Duration.ofMinutes(5);

    private static final String STAGE_HEARTBEAT_INTERVAL_SECONDS_KEY = "devflow.stage.heartbeat-seconds";
    private static final String DOCUMENT_STAGE_GENERATION_TIMEOUT_SECONDS_KEY =
            "devflow.stage.document-generation-timeout-seconds";
    private static final String EXECUTION_STAGE_GENERATION_TIMEOUT_SECONDS_KEY =
            "devflow.stage.execution-generation-timeout-seconds";
    private static final String DOCUMENT_STAGE_REVIEW_TIMEOUT_SECONDS_KEY =
            "devflow.stage.document-review-timeout-seconds";
    private static final String EXECUTION_STAGE_REVIEW_TIMEOUT_SECONDS_KEY =
            "devflow.stage.execution-review-timeout-seconds";

    public Duration generationHeartbeatInterval(StageType stageType) {
        return readDuration(STAGE_HEARTBEAT_INTERVAL_SECONDS_KEY, DEFAULT_STAGE_HEARTBEAT_INTERVAL);
    }

    public Duration generationTimeout(StageType stageType) {
        if (stageType == StageType.IMPLEMENTATION
                || stageType == StageType.CODE_REVIEW
                || stageType == StageType.TEST) {
            return readDuration(
                    EXECUTION_STAGE_GENERATION_TIMEOUT_SECONDS_KEY,
                    DEFAULT_EXECUTION_STAGE_GENERATION_TIMEOUT
            );
        }
        return readDuration(
                DOCUMENT_STAGE_GENERATION_TIMEOUT_SECONDS_KEY,
                DEFAULT_DOCUMENT_STAGE_GENERATION_TIMEOUT
        );
    }

    public Duration reviewHeartbeatInterval(StageType stageType) {
        return generationHeartbeatInterval(stageType);
    }

    public Duration reviewTimeout(StageType stageType) {
        if (stageType == StageType.IMPLEMENTATION
                || stageType == StageType.CODE_REVIEW
                || stageType == StageType.TEST) {
            return readDuration(
                    EXECUTION_STAGE_REVIEW_TIMEOUT_SECONDS_KEY,
                    DEFAULT_EXECUTION_STAGE_REVIEW_TIMEOUT
            );
        }
        return readDuration(
                DOCUMENT_STAGE_REVIEW_TIMEOUT_SECONDS_KEY,
                DEFAULT_DOCUMENT_STAGE_REVIEW_TIMEOUT
        );
    }

    private Duration readDuration(String key, Duration fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            long seconds = Long.parseLong(raw.trim());
            return seconds > 0 ? Duration.ofSeconds(seconds) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
