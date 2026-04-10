package devflow.agent.repair;

/**
 * diagnosis prompt 值对象。
 *
 * <p>统一承载 system/user prompt，避免 diagnosis 相关 builder 继续返回裸字符串对。
 */
record DiagnosisPrompt(
        String system,
        String user
) {
}
