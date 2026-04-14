package devflow.agent.executor.implementation.planning;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

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
