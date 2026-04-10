package devflow.agent.executor;

/**
 * 内联脚本 patch 执行结果。
 *
 * <p>正常情况下只返回最终脚本内容；如果在最小单元上仍然稳定触发
 * 结构性失败，则标记为需要执行宿主外提策略。
 */
record InlineScriptExecutionOutcome(
        String scriptContent,
        boolean externalizeToFile
) {
}
