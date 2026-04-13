package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * implementation planning 阶段的结构化提示词。
 */
record ImplementationPlanningPrompt(
        String systemPrompt,
        String userPrompt
) {
}
