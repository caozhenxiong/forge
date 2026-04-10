package devflow.agent.executor;

/**
 * 测试用例规划阶段的结构化提示词。
 *
 * <p>把系统提示词和用户提示词收成稳定对象，避免 `TestCasePlanner`
 * 继续直接拼接超长字符串并混入其他职责。
 */
record TestCaseGenerationPrompt(
        String systemPrompt,
        String userPrompt
) {
}
