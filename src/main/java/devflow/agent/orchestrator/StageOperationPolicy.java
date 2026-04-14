package devflow.agent.orchestrator;

import devflow.agent.domain.StageType;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 集中维护阶段级长调用的 heartbeat 与超时策略。
 *
 * <p>文档阶段的生成通常是单次较短调用；实现、代码审阅和测试阶段则可能
 * 包含多轮子任务、局部编辑和校验，不能再沿用同一条绝对超时。
 */
@ConfigurationProperties(prefix = "devflow.stage")
public record StageOperationPolicy(
        int heartbeatSeconds,
        int documentGenerationTimeoutSeconds,
        int executionGenerationTimeoutSeconds,
        int documentReviewTimeoutSeconds,
        int executionReviewTimeoutSeconds
) {

    private static final int DEFAULT_STAGE_HEARTBEAT_SECONDS = 20;
    private static final int DEFAULT_DOCUMENT_STAGE_GENERATION_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_EXECUTION_STAGE_GENERATION_TIMEOUT_SECONDS = 1_800;
    private static final int DEFAULT_DOCUMENT_STAGE_REVIEW_TIMEOUT_SECONDS = 180;
    private static final int DEFAULT_EXECUTION_STAGE_REVIEW_TIMEOUT_SECONDS = 300;

    public StageOperationPolicy() {
        this(
                DEFAULT_STAGE_HEARTBEAT_SECONDS,
                DEFAULT_DOCUMENT_STAGE_GENERATION_TIMEOUT_SECONDS,
                DEFAULT_EXECUTION_STAGE_GENERATION_TIMEOUT_SECONDS,
                DEFAULT_DOCUMENT_STAGE_REVIEW_TIMEOUT_SECONDS,
                DEFAULT_EXECUTION_STAGE_REVIEW_TIMEOUT_SECONDS
        );
    }

    public StageOperationPolicy {
        heartbeatSeconds = normalizePositive(heartbeatSeconds, DEFAULT_STAGE_HEARTBEAT_SECONDS);
        documentGenerationTimeoutSeconds = normalizePositive(
                documentGenerationTimeoutSeconds,
                DEFAULT_DOCUMENT_STAGE_GENERATION_TIMEOUT_SECONDS
        );
        executionGenerationTimeoutSeconds = normalizePositive(
                executionGenerationTimeoutSeconds,
                DEFAULT_EXECUTION_STAGE_GENERATION_TIMEOUT_SECONDS
        );
        documentReviewTimeoutSeconds = normalizePositive(
                documentReviewTimeoutSeconds,
                DEFAULT_DOCUMENT_STAGE_REVIEW_TIMEOUT_SECONDS
        );
        executionReviewTimeoutSeconds = normalizePositive(
                executionReviewTimeoutSeconds,
                DEFAULT_EXECUTION_STAGE_REVIEW_TIMEOUT_SECONDS
        );
    }

    public Duration generationHeartbeatInterval(StageType stageType) {
        return Duration.ofSeconds(heartbeatSeconds);
    }

    public Duration generationTimeout(StageType stageType) {
        if (stageType == StageType.IMPLEMENTATION
                || stageType == StageType.CODE_REVIEW
                || stageType == StageType.TEST) {
            return readDuration(
                    executionGenerationTimeoutSeconds
            );
        }
        return readDuration(
                documentGenerationTimeoutSeconds
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
                    executionReviewTimeoutSeconds
            );
        }
        return readDuration(
                documentReviewTimeoutSeconds
        );
    }

    private Duration readDuration(int seconds) {
        return Duration.ofSeconds(seconds);
    }

    private static int normalizePositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
