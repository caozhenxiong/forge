package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import devflow.agent.editing.precise.ExactReplaceApplySupport;
import devflow.agent.editing.precise.ExactReplaceEdit;
import devflow.agent.editing.precise.PreciseEditException;
import devflow.agent.editing.precise.PreciseEditFailureReason;
import java.nio.file.Path;

/**
 * 代码 patch 的 apply + verify 内核。
 *
 * <p>当前先覆盖两种最常见路径：
 * 1. 普通代码文件；
 * 2. HTML 内联脚本抽出的虚拟 JS 文档。
 *
 * <p>这层只负责本地 apply / verify，不做流程决策。
 */
public final class CodePatchKernel {

    private final ExactReplaceApplySupport exactReplaceApplySupport;
    private final PatchVerifier patchVerifier;

    public CodePatchKernel(PatchVerifier patchVerifier) {
        this.exactReplaceApplySupport = new ExactReplaceApplySupport();
        this.patchVerifier = patchVerifier;
    }

    PatchApplyResult applyInlineScript(
            Path relativePath,
            String currentScript,
            ExactReplaceEdit edit
    ) {
        try {
            String merged = exactReplaceApplySupport.applyEdit(
                    devflow.agent.util.ProjectPathSupport.inlineScriptSyntheticPath(relativePath).toString(),
                    currentScript,
                    edit
            );
            ToolResult verifyResult = patchVerifier.verifyInlineScript(relativePath, merged);
            if (verifyResult.succeeded()) {
                return new PatchApplyResult(
                        merged,
                        ToolResult.success(ToolName.PATCH_APPLY),
                        verifyResult
                );
            }
            return new PatchApplyResult(
                    merged,
                    ToolResult.success(ToolName.PATCH_APPLY),
                    verifyResult
            );
        } catch (PreciseEditException exception) {
            return new PatchApplyResult(
                    null,
                    preciseEditFailure(exception, "请保持 patch 目标与当前脚本工作集边界一致。"),
                    null
            );
        } catch (RuntimeException exception) {
            return new PatchApplyResult(
                    null,
                    ToolResult.failure(
                            ToolName.PATCH_APPLY,
                            ToolFailureCode.APPLY_FAILED,
                            exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                            "请保持 patch 目标与当前脚本工作集边界一致。"
                    ),
                    null
            );
        }
    }

    PatchApplyResult applyInlineStyle(
            Path relativePath,
            String currentStyle,
            ExactReplaceEdit edit
    ) {
        try {
            String merged = exactReplaceApplySupport.applyEdit(
                    devflow.agent.util.ProjectPathSupport.inlineStyleSyntheticPath(relativePath).toString(),
                    currentStyle,
                    edit
            );
            ToolResult verifyResult = patchVerifier.verifyInlineStyle(relativePath, merged);
            if (verifyResult.succeeded()) {
                return new PatchApplyResult(
                        merged,
                        ToolResult.success(ToolName.PATCH_APPLY),
                        verifyResult
                );
            }
            return new PatchApplyResult(
                    merged,
                    ToolResult.success(ToolName.PATCH_APPLY),
                    verifyResult
            );
        } catch (PreciseEditException exception) {
            return new PatchApplyResult(
                    null,
                    preciseEditFailure(exception, "请保持 patch 目标与当前样式工作集边界一致。"),
                    null
            );
        } catch (RuntimeException exception) {
            return new PatchApplyResult(
                    null,
                    ToolResult.failure(
                            ToolName.PATCH_APPLY,
                            ToolFailureCode.APPLY_FAILED,
                            exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                            "请保持 patch 目标与当前样式工作集边界一致。"
                    ),
                    null
            );
        }
    }

    public PatchApplyResult applyCodeFile(
            Path projectPath,
            Path relativePath,
            String currentContent,
            ExactReplaceEdit edit
    ) {
        try {
            String merged = exactReplaceApplySupport.applyEdit(relativePath.toString(), currentContent, edit);
            ToolResult verifyResult = patchVerifier.verifyCodeFile(projectPath, relativePath, merged);
            if (verifyResult.succeeded()) {
                return new PatchApplyResult(
                        merged,
                        ToolResult.success(ToolName.PATCH_APPLY),
                        verifyResult
                );
            }
            return new PatchApplyResult(
                    merged,
                    ToolResult.success(ToolName.PATCH_APPLY),
                    verifyResult
            );
        } catch (PreciseEditException exception) {
            return new PatchApplyResult(
                    null,
                    preciseEditFailure(exception, "请保持 patch 目标与当前文件可编辑符号一致。"),
                    null
            );
        } catch (RuntimeException exception) {
            return new PatchApplyResult(
                    null,
                    ToolResult.failure(
                            ToolName.PATCH_APPLY,
                            ToolFailureCode.APPLY_FAILED,
                            exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                            "请保持 patch 目标与当前文件可编辑符号一致。"
                    ),
                    null
            );
        }
    }

    /**
     * 本地 patch apply 失败时要保留结构化失败原因，
     * 不能再把所有问题都折叠成同一个 APPLY_FAILED。
     */
    private ToolResult preciseEditFailure(PreciseEditException exception, String defaultNextAction) {
        PreciseEditFailureReason reason = exception.reason();
        ToolFailureCode failureCode = preciseEditFailureCode(reason);
        return ToolResult.failure(
                ToolName.PATCH_APPLY,
                failureCode,
                exception.getMessage() == null ? reason.name() : exception.getMessage(),
                defaultNextAction
        );
    }

    private ToolFailureCode preciseEditFailureCode(PreciseEditFailureReason reason) {
        if (reason == PreciseEditFailureReason.NO_MATERIAL_CHANGE || reason == PreciseEditFailureReason.MODEL_OUTPUT_INVALID) {
            return ToolFailureCode.MODEL_OUTPUT_INVALID;
        }
        if (reason == PreciseEditFailureReason.TARGET_NOT_FOUND) {
            return ToolFailureCode.TARGET_NOT_FOUND;
        }
        if (reason == PreciseEditFailureReason.SNAPSHOT_STALE) {
            return ToolFailureCode.SNAPSHOT_STALE;
        }
        if (reason == PreciseEditFailureReason.TARGET_NOT_UNIQUE) {
            return ToolFailureCode.TARGET_NOT_UNIQUE;
        }
        if (reason == PreciseEditFailureReason.TARGET_SCOPE_VIOLATION) {
            return ToolFailureCode.TARGET_SCOPE_VIOLATION;
        }
        return ToolFailureCode.TARGET_NOT_ADDRESSABLE;
    }
}
