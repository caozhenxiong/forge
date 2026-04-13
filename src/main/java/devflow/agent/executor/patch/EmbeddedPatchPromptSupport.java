package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 统一维护宿主内嵌 patch 单元的 prompt 组装。
 */
public final class EmbeddedPatchPromptSupport {

    private final PatchContextBuilder patchContextBuilder;
    private final PatchExecutionSupport executionSupport;

    public EmbeddedPatchPromptSupport(PatchContextBuilder patchContextBuilder, PatchExecutionSupport executionSupport) {
        this.patchContextBuilder = patchContextBuilder;
        this.executionSupport = executionSupport;
    }

    PatchGenerationPrompt assemble(
            EmbeddedTargetedRewriteRequest request,
            EmbeddedPatchKind patchKind,
            String currentContent,
            EditUnit unit
    ) {
        return EmbeddedPatchPromptAssembler.assemble(
                request.relativePath(),
                request.planSummary(),
                request.taskPackageMarkdown(),
                executionSupport.nullToEmpty(request.coderContextMarkdown()),
                request.reason(),
                executionSupport.nullToEmpty(request.feedback()),
                request.targetedContext(),
                patchKind,
                unit,
                currentContent,
                patchKind.describeTargets(patchContextBuilder, request.relativePath(), currentContent),
                executionSupport.summarizeForVerification(currentContent, patchKind.previewChars()),
                isRestrictedUnit(unit),
                isStrictAppendOnlyUnit(unit)
        );
    }

    String retryPrompt(
            PatchGenerationPrompt generationPrompt,
            EmbeddedPatchKind patchKind,
            String retryFeedback
    ) {
        return RetryPromptComposer.append(
                generationPrompt.userPrompt(),
                retryFeedback,
                "上一轮" + patchKind.displayName() + "工作集改写失败，请修正后重新生成："
        );
    }

    private boolean isRestrictedUnit(EditUnit unit) {
        return unit != null && unit.restrictsSymbols();
    }

    private boolean isStrictAppendOnlyUnit(EditUnit unit) {
        return unit != null && unit.appendOnly() && !unit.splittable();
    }
}
