package devflow.agent.executor;

/**
 * patch apply + verify 的统一结果。
 */
record PatchApplyResult(
        String content,
        ToolResult applyResult,
        ToolResult verifyResult
) {
    boolean succeeded() {
        return content != null
                && applyResult != null
                && applyResult.succeeded()
                && (verifyResult == null || verifyResult.succeeded());
    }

    ToolResult failureResult() {
        if (applyResult != null && !applyResult.succeeded()) {
            return applyResult;
        }
        if (verifyResult != null && !verifyResult.succeeded()) {
            return verifyResult;
        }
        return null;
    }
}
