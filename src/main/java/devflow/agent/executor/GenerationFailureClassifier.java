package devflow.agent.executor;

import devflow.agent.editing.PreciseEditException;
import devflow.agent.editing.PreciseEditFailureReason;

/**
 * 统一把执行链异常映射成稳定的生成失败类型。
 *
 * <p>这层只负责“异常 -> failure type”的确定性分类，不负责证据拼装或重试文案，
 * 这样协调器和执行器都不需要重复维护同一套异常分流逻辑。
 */
final class GenerationFailureClassifier {

    GenerationFailureType classify(Exception exception) {
        LlmInvocationException llmInvocationException = findCause(exception, LlmInvocationException.class);
        if (llmInvocationException != null) {
            return classifyInvocationFailure(llmInvocationException.reason());
        }
        StructuredPayloadException structuredPayloadException = findCause(exception, StructuredPayloadException.class);
        if (structuredPayloadException != null) {
            return GenerationFailureType.INVALID_PATCH_JSON;
        }
        PreciseEditException preciseEditException = findCause(exception, PreciseEditException.class);
        if (preciseEditException != null) {
            return classifyPreciseEditFailure(preciseEditException.reason());
        }
        return GenerationFailureType.MODEL_INVOCATION_FAILED;
    }

    private GenerationFailureType classifyPreciseEditFailure(PreciseEditFailureReason reason) {
        if (reason == null) {
            return GenerationFailureType.RESULT_FILE_INVALID;
        }
        if (reason == PreciseEditFailureReason.SYMBOL_NOT_FOUND) {
            return GenerationFailureType.SYMBOL_NOT_FOUND;
        }
        if (reason == PreciseEditFailureReason.PATCH_EMPTY || reason == PreciseEditFailureReason.PATCH_SCHEMA_INVALID) {
            return GenerationFailureType.PATCH_SCHEMA_INVALID;
        }
        if (reason == PreciseEditFailureReason.EDIT_UNIT_SCOPE_VIOLATION) {
            return GenerationFailureType.EDIT_UNIT_SCOPE_VIOLATION;
        }
        return GenerationFailureType.RESULT_FILE_INVALID;
    }

    private GenerationFailureType classifyInvocationFailure(LlmFailureReason reason) {
        if (reason == LlmFailureReason.TIMEOUT) {
            return GenerationFailureType.ATTEMPT_TIMEOUT;
        }
        if (reason == LlmFailureReason.OUTPUT_TRUNCATED) {
            return GenerationFailureType.OUTPUT_TRUNCATED;
        }
        return GenerationFailureType.MODEL_INVOCATION_FAILED;
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
