package devflow.agent.executor;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * patch 单元失败后的反馈策略。
 *
 * <p>执行器不应该继续硬编码：
 * 1. 哪些失败该 abort；
 * 2. 哪些失败该 retry；
 * 3. 每种失败要回什么提示。
 *
 * <p>这里把反馈文案和失败语义绑定成结构化策略，执行器只负责把当前单元失败交给统一支撑处理。
 */
record PatchAttemptFeedbackPolicy(
        Supplier<String> abortFeedback,
        IntFunction<String> truncationRetryFeedback,
        IntFunction<String> invalidJsonFeedback,
        IntFunction<String> patchSchemaFeedback,
        IntFunction<String> scopeViolationFeedback,
        Function<String, String> symbolNotFoundFeedback
) {
}
