package devflow.agent.executor;

/**
 * 宿主内嵌 patch 单元执行结果。
 *
 * <p>当前先覆盖两种收尾动作：
 * 1. 返回最终嵌入片段内容；
 * 2. 对内联脚本这类连续失败但可结构性改道的场景，标记需要外提到独立文件。
 */
record EmbeddedPatchOutcome(
        String content,
        boolean externalizeToFile
) {

    static EmbeddedPatchOutcome contentOnly(String content) {
        return new EmbeddedPatchOutcome(content, false);
    }

    static EmbeddedPatchOutcome externalize(String content) {
        return new EmbeddedPatchOutcome(content, true);
    }
}
