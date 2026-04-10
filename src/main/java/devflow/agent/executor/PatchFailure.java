package devflow.agent.executor;

/**
 * patch 单元执行失败的结构化快照。
 *
 * <p>这层现在既能承接 generation failure，也能承接本地工具失败：
 * 1. 统一保留流程侧 failure type；
 * 2. 额外保留工具来源、工具失败码和推荐动作；
 * 3. 让 router 和 coordinator 可以优先依赖结构化工具结果，而不是回退到字符串或异常消息。
 */
record PatchFailure(
        GenerationFailureType failureType,
        ToolName toolName,
        ToolFailureCode toolFailureCode,
        String evidence,
        String recommendedNextAction
) {

    PatchFailure {
        evidence = evidence == null ? "" : evidence;
        recommendedNextAction = recommendedNextAction == null ? "" : recommendedNextAction;
    }

    static PatchFailure of(GenerationFailureType failureType, String evidence) {
        return new PatchFailure(failureType, null, null, evidence, "");
    }

    static PatchFailure fromToolResult(ToolResult toolResult, GenerationFailureType fallback) {
        if (toolResult == null) {
            return of(fallback, "");
        }
        return new PatchFailure(
                mapFailureType(toolResult.failureCode(), fallback),
                toolResult.toolName(),
                toolResult.failureCode(),
                toolResult.evidence(),
                toolResult.recommendedNextAction()
        );
    }

    String recommendedNextActionOr(String fallback) {
        return recommendedNextAction.isBlank() ? fallback : recommendedNextAction;
    }

    private static GenerationFailureType mapFailureType(ToolFailureCode failureCode, GenerationFailureType fallback) {
        if (failureCode == null) {
            return fallback;
        }
        if (failureCode == ToolFailureCode.PATCH_APPLY_FAILED || failureCode == ToolFailureCode.PATCH_SCHEMA_INVALID) {
            return GenerationFailureType.PATCH_SCHEMA_INVALID;
        }
        if (failureCode == ToolFailureCode.PATCH_SCOPE_VIOLATION) {
            return GenerationFailureType.EDIT_UNIT_SCOPE_VIOLATION;
        }
        if (failureCode == ToolFailureCode.PATCH_SYMBOL_NOT_FOUND) {
            return GenerationFailureType.SYMBOL_NOT_FOUND;
        }
        if (failureCode == ToolFailureCode.PATCH_ANCHOR_MISSING) {
            return GenerationFailureType.RESULT_FILE_INVALID;
        }
        if (failureCode == ToolFailureCode.TREE_SITTER_PARSE_FAILED) {
            return GenerationFailureType.TREE_SITTER_PARSE_FAILED;
        }
        if (failureCode == ToolFailureCode.GENERATED_CONTENT_EMPTY
                || failureCode == ToolFailureCode.HTML_STRUCTURE_INVALID
                || failureCode == ToolFailureCode.INLINE_SCRIPT_INVALID
                || failureCode == ToolFailureCode.CONTENT_VALIDATION_EXCEPTION
                || failureCode == ToolFailureCode.GENERATED_CONTENT_INVALID
                || failureCode == ToolFailureCode.JAVASCRIPT_SYNTAX_INVALID) {
            return GenerationFailureType.RESULT_FILE_INVALID;
        }
        return fallback;
    }
}
