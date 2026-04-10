package devflow.agent.artifact;

/**
 * 文档阶段一次生成调用使用的 system/user prompt 对。
 */
record DocumentGenerationPrompt(String system, String user) {
}
