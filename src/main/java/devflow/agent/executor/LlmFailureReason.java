package devflow.agent.executor;

/**
 * LLM 调用层的稳定失败原因。
 *
 * <p>流程控制不应再通过解析异常消息文本来判断“这是超时、截断还是普通调用失败”，
 * 而应优先消费这里的类型化原因。
 */
public enum LlmFailureReason {
    TIMEOUT,
    OUTPUT_TRUNCATED,
    EMPTY_RESPONSE,
    TRANSPORT,
    INVALID_RESPONSE
}
