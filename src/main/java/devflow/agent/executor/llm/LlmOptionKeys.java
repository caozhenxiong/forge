package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 集中定义 LLM 调用层使用的稳定 option key。
 *
 * <p>这些键属于对外协议的一部分，不应继续散落为魔法字符串。
 */
public final class LlmOptionKeys {

    public static final String NUM_CTX = "num_ctx";
    public static final String NUM_PREDICT = "num_predict";
    public static final String OUTPUT_BUDGET_RATIO = "devflow_output_budget_ratio";

    private LlmOptionKeys() {
    }
}
