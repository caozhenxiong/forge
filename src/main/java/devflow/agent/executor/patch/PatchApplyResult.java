package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolResult;

/**
 * patch apply + verify 的统一结果。
 */
public record PatchApplyResult(
        String content,
        ToolResult applyResult,
        ToolResult verifyResult
) {
    public boolean succeeded() {
        return content != null
                && applyResult != null
                && applyResult.succeeded()
                && (verifyResult == null || verifyResult.succeeded());
    }

    public ToolResult failureResult() {
        if (applyResult != null && !applyResult.succeeded()) {
            return applyResult;
        }
        if (verifyResult != null && !verifyResult.succeeded()) {
            return verifyResult;
        }
        return null;
    }
}
