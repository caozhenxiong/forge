package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 编码主链的唯一模型能力契约。
 *
 * <p>implementation tool loop 必须显式依赖 chat/tool provider，
 * 不能再从通用 generate 接口桥接出伪 chat 语义。
 */
public interface ChatCapableLlmProvider {

    LlmChatResponse chat(LlmChatRequest request);
}
