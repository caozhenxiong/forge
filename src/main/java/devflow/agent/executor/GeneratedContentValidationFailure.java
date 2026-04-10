package devflow.agent.executor;

/**
 * 生成内容本地校验失败的结构化结果。
 */
record GeneratedContentValidationFailure(
        GeneratedContentValidationCode code,
        String message
) {
}
