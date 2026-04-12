package devflow.agent.executor;

/**
 * 生成内容本地校验的稳定失败码。
 *
 * <p>这里显式区分“内容为空”“结构不完整”“语法不可解析”等类型，
 * 避免后续再通过错误消息文本反推失败类型。
 */
enum GeneratedContentValidationCode {
    EMPTY_OUTPUT(GenerationFailureType.VALIDATION_FAILED, ToolFailureCode.GENERATED_CONTENT_EMPTY),
    HTML_STRUCTURE_INVALID(GenerationFailureType.VALIDATION_FAILED, ToolFailureCode.HTML_STRUCTURE_INVALID),
    RUNTIME_WIRING_INVALID(GenerationFailureType.VALIDATION_FAILED, ToolFailureCode.RUNTIME_WIRING_INVALID),
    JAVASCRIPT_STRUCTURE_INVALID(GenerationFailureType.VALIDATION_FAILED, ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID),
    PRECISE_EDIT_ANCHORS_MISSING(GenerationFailureType.VALIDATION_FAILED, ToolFailureCode.TARGET_NOT_ADDRESSABLE),
    SYNTAX_INVALID(GenerationFailureType.SYNTAX_INVALID, ToolFailureCode.SYNTAX_INVALID),
    JAVASCRIPT_CHECK_FAILED(GenerationFailureType.VALIDATION_FAILED, ToolFailureCode.JAVASCRIPT_SYNTAX_INVALID),
    INLINE_SCRIPT_INVALID(GenerationFailureType.VALIDATION_FAILED, ToolFailureCode.INLINE_SCRIPT_INVALID),
    VALIDATION_EXCEPTION(GenerationFailureType.VALIDATION_FAILED, ToolFailureCode.CONTENT_VALIDATION_EXCEPTION);

    private final GenerationFailureType failureType;
    private final ToolFailureCode toolFailureCode;

    GeneratedContentValidationCode(GenerationFailureType failureType, ToolFailureCode toolFailureCode) {
        this.failureType = failureType;
        this.toolFailureCode = toolFailureCode;
    }

    GenerationFailureType failureType() {
        return failureType;
    }

    ToolFailureCode toolFailureCode() {
        return toolFailureCode;
    }
}
