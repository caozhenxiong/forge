package devflow.agent.executor;

/**
 * 统一判定 patch 失败是否适合进入 repair-before-regenerate。
 */
final class PatchRepairClassifier {

    PatchFailureClass classify(PatchFailure failure) {
        if (failure == null) {
            return PatchFailureClass.NON_MECHANICAL;
        }
        return switch (failure.failureType()) {
            case INVALID_PATCH_JSON, PATCH_SCHEMA_INVALID, TREE_SITTER_PARSE_FAILED -> PatchFailureClass.MECHANICAL;
            case RESULT_FILE_INVALID -> classifyResultFileFailure(failure);
            case MODEL_INVOCATION_FAILED,
                    ATTEMPT_TIMEOUT,
                    OUTPUT_TRUNCATED,
                    EDIT_UNIT_SCOPE_VIOLATION,
                    SYMBOL_NOT_FOUND -> PatchFailureClass.NON_MECHANICAL;
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
        if (failure.failureType() == GenerationFailureType.EDIT_UNIT_SCOPE_VIOLATION
                || failure.failureType() == GenerationFailureType.OUTPUT_TRUNCATED) {
            return false;
        }
        return failure.failureType() == GenerationFailureType.TREE_SITTER_PARSE_FAILED
                || failure.toolFailureCode() == ToolFailureCode.TREE_SITTER_PARSE_FAILED
                || failure.toolFailureCode() == ToolFailureCode.JAVASCRIPT_SYNTAX_INVALID
                || failure.toolFailureCode() == ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID
                || failure.toolFailureCode() == ToolFailureCode.INLINE_SCRIPT_INVALID;
    }

    private PatchFailureClass classifyResultFileFailure(PatchFailure failure) {
        ToolFailureCode code = failure.toolFailureCode();
        if (code == ToolFailureCode.TREE_SITTER_PARSE_FAILED
                || code == ToolFailureCode.JAVASCRIPT_SYNTAX_INVALID
                || code == ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID
                || code == ToolFailureCode.INLINE_SCRIPT_INVALID
                || code == ToolFailureCode.HTML_STRUCTURE_INVALID
                || code == ToolFailureCode.PATCH_SCHEMA_INVALID) {
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
