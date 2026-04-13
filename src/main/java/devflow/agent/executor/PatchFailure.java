package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import devflow.agent.executor.generation.GenerationFailureType;

/**
 * patch 单元执行失败的结构化快照。
 *
 * <p>这层现在既能承接 generation failure，也能承接本地工具失败：
 * 1. 统一保留流程侧 failure type；
 * 2. 额外保留工具来源、工具失败码和推荐动作；
 * 3. 让 router 和 coordinator 可以优先依赖结构化工具结果，而不是回退到字符串或异常消息。
 */
public record PatchFailure(
        GenerationFailureType failureType,
        ToolName toolName,
        ToolFailureCode toolFailureCode,
        String evidence,
        String recommendedNextAction
) {

    public PatchFailure {
        evidence = evidence == null ? "" : evidence;
        recommendedNextAction = recommendedNextAction == null ? "" : recommendedNextAction;
    }

    public static PatchFailure of(GenerationFailureType failureType, String evidence) {
        return new PatchFailure(failureType, null, null, evidence, "");
    }

    public static PatchFailure fromToolResult(ToolResult toolResult, GenerationFailureType fallback) {
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

    public String recommendedNextActionOr(String fallback) {
        return recommendedNextAction.isBlank() ? fallback : recommendedNextAction;
    }

    private static GenerationFailureType mapFailureType(ToolFailureCode failureCode, GenerationFailureType fallback) {
        if (failureCode == null) {
            return fallback;
        }
        if (failureCode == ToolFailureCode.APPLY_FAILED || failureCode == ToolFailureCode.MODEL_OUTPUT_INVALID) {
            return GenerationFailureType.MODEL_OUTPUT_INVALID;
        }
        if (failureCode == ToolFailureCode.TARGET_SCOPE_VIOLATION) {
            return GenerationFailureType.TARGET_SCOPE_VIOLATION;
        }
        if (failureCode == ToolFailureCode.TARGET_NOT_FOUND) {
            return GenerationFailureType.TARGET_NOT_FOUND;
        }
        if (failureCode == ToolFailureCode.TARGET_NOT_UNIQUE) {
            return GenerationFailureType.TARGET_NOT_UNIQUE;
        }
        if (failureCode == ToolFailureCode.SNAPSHOT_STALE) {
            return GenerationFailureType.SNAPSHOT_STALE;
        }
        if (failureCode == ToolFailureCode.NO_MATERIAL_CHANGE) {
            return GenerationFailureType.NO_MATERIAL_CHANGE;
        }
        if (failureCode == ToolFailureCode.TARGET_NOT_ADDRESSABLE) {
            return GenerationFailureType.VALIDATION_FAILED;
        }
        if (failureCode == ToolFailureCode.SYNTAX_INVALID) {
            return GenerationFailureType.SYNTAX_INVALID;
        }
        if (failureCode == ToolFailureCode.GENERATED_CONTENT_EMPTY
                || failureCode == ToolFailureCode.HTML_STRUCTURE_INVALID
                || failureCode == ToolFailureCode.INLINE_SCRIPT_INVALID
                || failureCode == ToolFailureCode.CONTENT_VALIDATION_EXCEPTION
                || failureCode == ToolFailureCode.GENERATED_CONTENT_INVALID
                || failureCode == ToolFailureCode.JAVASCRIPT_SYNTAX_INVALID) {
            return GenerationFailureType.VALIDATION_FAILED;
        }
        return fallback;
    }
}
