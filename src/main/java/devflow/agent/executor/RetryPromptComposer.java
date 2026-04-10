package devflow.agent.executor;

/**
 * 统一拼接“上一轮失败后的下一轮重试提示”。
 *
 * <p>多条生成链都会在原始 user prompt 后追加一段纠错说明；这层把拼接规则集中，
 * 避免协调器继续散落近似字面量。
 */
final class RetryPromptComposer {

    private RetryPromptComposer() {
    }

    static String append(String basePrompt, String retryFeedback, String retryTitle) {
        if (retryFeedback == null || retryFeedback.isBlank()) {
            return basePrompt;
        }
        return basePrompt + "\n\n" + retryTitle + "\n" + retryFeedback;
    }
}
