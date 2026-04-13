package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmFailureReason;
import devflow.agent.executor.llm.LlmInvocationException;
import devflow.agent.editing.PreciseEditException;
import devflow.agent.editing.PreciseEditFailureReason;
import devflow.agent.executor.llm.StructuredPayloadException;

/**
 * 统一把执行链异常映射成稳定的生成失败类型。
 *
 * <p>这层只负责“异常 -> failure type”的确定性分类，不负责证据拼装或重试文案，
 * 这样协调器和执行器都不需要重复维护同一套异常分流逻辑。
 */
public final class GenerationFailureClassifier {

    public GenerationFailureType classify(Exception exception) {
        LlmInvocationException llmInvocationException = findCause(exception, LlmInvocationException.class);
        if (llmInvocationException != null) {
            return classifyInvocationFailure(llmInvocationException.reason());
        }
        StructuredPayloadException structuredPayloadException = findCause(exception, StructuredPayloadException.class);
        if (structuredPayloadException != null) {
            return GenerationFailureType.MODEL_OUTPUT_INVALID;
        }
        PreciseEditException preciseEditException = findCause(exception, PreciseEditException.class);
        if (preciseEditException != null) {
            return classifyPreciseEditFailure(preciseEditException.reason());
        }
        return GenerationFailureType.MODEL_INVOCATION_FAILED;
    }

    private GenerationFailureType classifyPreciseEditFailure(PreciseEditFailureReason reason) {
        if (reason == null) {
            return GenerationFailureType.VALIDATION_FAILED;
        }
        if (reason == PreciseEditFailureReason.SNAPSHOT_STALE) {
            return GenerationFailureType.SNAPSHOT_STALE;
        }
        if (reason == PreciseEditFailureReason.TARGET_NOT_FOUND) {
            return GenerationFailureType.TARGET_NOT_FOUND;
        }
        if (reason == PreciseEditFailureReason.TARGET_NOT_UNIQUE) {
            return GenerationFailureType.TARGET_NOT_UNIQUE;
        }
        if (reason == PreciseEditFailureReason.NO_MATERIAL_CHANGE) {
            return GenerationFailureType.NO_MATERIAL_CHANGE;
        }
        if (reason == PreciseEditFailureReason.MODEL_OUTPUT_INVALID) {
            return GenerationFailureType.MODEL_OUTPUT_INVALID;
        }
        if (reason == PreciseEditFailureReason.TARGET_SCOPE_VIOLATION) {
            return GenerationFailureType.TARGET_SCOPE_VIOLATION;
        }
        return GenerationFailureType.VALIDATION_FAILED;
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
