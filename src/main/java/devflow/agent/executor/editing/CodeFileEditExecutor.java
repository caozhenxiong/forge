package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureType;

/**
 * 代码文件 patch 执行门面。
 *
 * <p>它统一接管：
 * 1. `precise-code` 主链；
 * 2. 本地局部编辑的强制约束；
 * 3. 代码文件 whole-file 例外路径。
 */
public final class CodeFileEditExecutor {

    private final FileEditStrategyResolver fileEditStrategyResolver;
    private final PreciseCodePatchExecutor preciseCodePatchExecutor;
    private final FullRewriteExecutor wholeFilePatchExecutor;
    private final FileProtocolRequestFactory patchRequestFactory;
    private final FileGenerationFailureFactory fileGenerationFailureFactory;
    private final int maxFileGenerationAttempts;

    public CodeFileEditExecutor(
            FileEditStrategyResolver fileEditStrategyResolver,
            PreciseCodePatchExecutor preciseCodePatchExecutor,
            FullRewriteExecutor wholeFilePatchExecutor,
            FileProtocolRequestFactory patchRequestFactory,
            FileGenerationFailureFactory fileGenerationFailureFactory,
            int maxFileGenerationAttempts
    ) {
        this.fileEditStrategyResolver = fileEditStrategyResolver;
        this.preciseCodePatchExecutor = preciseCodePatchExecutor;
        this.wholeFilePatchExecutor = wholeFilePatchExecutor;
        this.patchRequestFactory = patchRequestFactory;
        this.fileGenerationFailureFactory = fileGenerationFailureFactory;
        this.maxFileGenerationAttempts = maxFileGenerationAttempts;
    }

    public GeneratedFileOutput generate(FileEditRequest request) {
        if (request.editAttemptState() != null
                && request.editAttemptState().matches(request.relativePath(), FileEditStrategyNames.PRECISE_CODE)) {
            return GeneratedFileOutput.primaryOnly(preciseCodePatchExecutor.generate(
                    patchRequestFactory.code(request)
            ));
        }
        if (fileEditStrategyResolver.shouldUsePreciseCodeEditing(
                request.projectPath(),
                request.relativePath(),
                request.executionState().deliveryMode(),
                request.executionState().preferPreciseEditing(),
                request.existingContent()
        )) {
            return GeneratedFileOutput.primaryOnly(preciseCodePatchExecutor.generate(
                    patchRequestFactory.code(request)
            ));
        }
        if (fileEditStrategyResolver.mustUseLocalCodeEditing(
                request.projectPath(),
                request.relativePath(),
                request.executionState().deliveryMode()
        )) {
            throw fileGenerationFailureFactory.create(
                    request.relativePath(),
                    request.executionState().deliveryMode(),
                    FileEditStrategyNames.LOCAL_CODE_EDIT_REQUIRED,
                    maxFileGenerationAttempts,
                    GenerationFailureType.TARGET_NOT_FOUND,
                    "已有代码文件缺少可用的局部编辑边界，当前执行策略禁止退回整文件重写。",
                    """
                            请保留当前文件结构，重新聚焦到已有符号、方法体或可追加的最小工作集；
                            如果确实需要整体改写，应显式升级为 REWORK，而不是在 PATCH/INCREMENTAL 中静默走整文件重写。
                            """
            );
        }
        if (!fileEditStrategyResolver.canUseWholeFileRewriteForCode(
                request.projectPath(),
                request.relativePath(),
                request.executionState().deliveryMode()
        )) {
            throw fileGenerationFailureFactory.create(
                    request.relativePath(),
                    request.executionState().deliveryMode(),
                    FileEditStrategyNames.FULL_FILE_DISALLOWED,
                    maxFileGenerationAttempts,
                    GenerationFailureType.VALIDATION_FAILED,
                    "当前代码文件不允许走整文件重写主路径。",
                    "请改用局部代码编辑或 continuation；若需要整体改写，请升级为 REWORK。"
            );
        }
        return GeneratedFileOutput.primaryOnly(wholeFilePatchExecutor.generate(
                patchRequestFactory.wholeFile(request)
        ));
    }
}
