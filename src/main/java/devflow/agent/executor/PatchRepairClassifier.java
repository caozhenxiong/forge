package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;

import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.llm.StructuredPayloadException;
import devflow.agent.executor.llm.StructuredPayloadFailureReason;

/**
 * 统一判定 patch 失败是否适合进入 repair-before-regenerate。
 */
final class PatchRepairClassifier {

    PatchFailureClass classify(PatchFailure failure) {
        if (failure == null) {
            return PatchFailureClass.NON_MECHANICAL;
        }
        return switch (failure.failureType()) {
            case MODEL_OUTPUT_INVALID, SYNTAX_INVALID -> PatchFailureClass.MECHANICAL;
            case VALIDATION_FAILED -> classifyResultFileFailure(failure);
            case MODEL_INVOCATION_FAILED,
                    ATTEMPT_TIMEOUT,
                    OUTPUT_TRUNCATED,
                    TARGET_SCOPE_VIOLATION,
                    TARGET_NOT_FOUND,
                    TARGET_NOT_UNIQUE,
                    SNAPSHOT_STALE,
                    NO_MATERIAL_CHANGE -> PatchFailureClass.NON_MECHANICAL;
        };
    }

    boolean supportsJsonRepair(Exception exception) {
        StructuredPayloadException structuredPayloadException = findStructuredPayloadException(exception);
        if (structuredPayloadException == null) {
            return false;
        }
        return structuredPayloadException.reason() == StructuredPayloadFailureReason.JSON_OBJECT_MISSING
                || structuredPayloadException.reason() == StructuredPayloadFailureReason.JSON_PAYLOAD_INVALID;
    }

    boolean supportsSyntaxRepair(PatchFailure failure) {
        if (failure == null || classify(failure) != PatchFailureClass.MECHANICAL) {
            return false;
        }
        if (failure.failureType() == GenerationFailureType.TARGET_SCOPE_VIOLATION
                || failure.failureType() == GenerationFailureType.OUTPUT_TRUNCATED) {
            return false;
        }
        return failure.failureType() == GenerationFailureType.SYNTAX_INVALID
                || failure.toolFailureCode() == ToolFailureCode.SYNTAX_INVALID
                || failure.toolFailureCode() == ToolFailureCode.JAVASCRIPT_SYNTAX_INVALID
                || failure.toolFailureCode() == ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID
                || failure.toolFailureCode() == ToolFailureCode.INLINE_SCRIPT_INVALID;
    }

    boolean supportsExactReplaceSemanticRepair(PatchFailure failure) {
        if (failure == null || failure.toolFailureCode() == null) {
            return false;
        }
        return failure.toolFailureCode() == ToolFailureCode.MODEL_OUTPUT_INVALID
                || failure.toolFailureCode() == ToolFailureCode.SNAPSHOT_STALE
                || failure.toolFailureCode() == ToolFailureCode.TARGET_NOT_FOUND
                || failure.toolFailureCode() == ToolFailureCode.TARGET_NOT_UNIQUE;
    }

    private PatchFailureClass classifyResultFileFailure(PatchFailure failure) {
        ToolFailureCode code = failure.toolFailureCode();
        if (code == ToolFailureCode.SYNTAX_INVALID
                || code == ToolFailureCode.JAVASCRIPT_SYNTAX_INVALID
                || code == ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID
                || code == ToolFailureCode.INLINE_SCRIPT_INVALID
                || code == ToolFailureCode.HTML_STRUCTURE_INVALID
                || code == ToolFailureCode.MODEL_OUTPUT_INVALID) {
            return PatchFailureClass.MECHANICAL;
        }
        return PatchFailureClass.NON_MECHANICAL;
    }

    private StructuredPayloadException findStructuredPayloadException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof StructuredPayloadException structuredPayloadException) {
                return structuredPayloadException;
            }
            current = current.getCause();
        }
        return null;
    }
}
