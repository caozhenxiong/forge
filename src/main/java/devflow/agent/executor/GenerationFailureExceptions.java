package devflow.agent.executor;

/**
 * 统一创建生成链失败异常。
 *
 * <p>把 `GenerationFailureReport` 的公共装配约束收成单点，避免不同执行器继续各自拼装
 * `evidence / retryHint / retryable` 这些稳定字段。
 */
public final class GenerationFailureExceptions {

    private GenerationFailureExceptions() {
    }

    public static GenerationFailureException create(
            String artifactPath,
            String deliveryMode,
            String strategy,
            GenerationFailureType failureType,
            int attempts,
            boolean retryable,
            String summary,
            String evidence,
            String retryHint
    ) {
        return new GenerationFailureException(
                new GenerationFailureReport(
                        artifactPath,
                        deliveryMode,
                        strategy,
                        failureType,
                        attempts,
                        retryable,
                        summary == null ? "" : summary,
                        evidence == null ? "" : evidence,
                        retryHint == null ? "" : retryHint
                )
        );
    }
}
