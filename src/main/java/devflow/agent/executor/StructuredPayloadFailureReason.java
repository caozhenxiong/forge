package devflow.agent.executor;

/**
 * 结构化模型载荷解析失败的稳定原因。
 */
enum StructuredPayloadFailureReason {
    JSON_OBJECT_MISSING,
    JSON_PAYLOAD_INVALID
}
