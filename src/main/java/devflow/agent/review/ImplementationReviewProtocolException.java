package devflow.agent.review;

/**
 * implementation review 结构化协议不完整时抛出的异常。
 *
 * <p>这类错误说明 review 输出缺少主链继续执行所必需的结构化字段，
 * 不能再降级成 prose 驱动的 continuation。
 */
final class ImplementationReviewProtocolException extends RuntimeException {

    ImplementationReviewProtocolException(String message) {
        super(message);
    }
}
