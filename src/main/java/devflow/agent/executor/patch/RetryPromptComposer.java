package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 统一拼接“上一轮失败后的下一轮重试提示”。
 *
 * <p>多条生成链都会在原始 user prompt 后追加一段纠错说明；这层把拼接规则集中，
 * 避免协调器继续散落近似字面量。
 */
public final class RetryPromptComposer {

    private RetryPromptComposer() {
    }

    public static String append(String basePrompt, String retryFeedback, String retryTitle) {
        if (retryFeedback == null || retryFeedback.isBlank()) {
            return basePrompt;
        }
        return basePrompt + "\n\n" + retryTitle + "\n" + retryFeedback;
    }
}
