package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 结构化模型载荷解析失败的稳定原因。
 */
public enum StructuredPayloadFailureReason {
    JSON_OBJECT_MISSING,
    JSON_PAYLOAD_INVALID
}
