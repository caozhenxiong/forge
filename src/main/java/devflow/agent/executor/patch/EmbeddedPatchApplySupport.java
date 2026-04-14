package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.precise.ExactReplaceEdit;

/**
 * 统一维护宿主内嵌 patch 的 decode / validate / apply。
 */
public final class EmbeddedPatchApplySupport {

    private final CodePatchKernel codePatchKernel;
    private final PatchPayloadRepairSupport patchPayloadRepairSupport;

    public EmbeddedPatchApplySupport(
            PatchPayloadRepairSupport patchPayloadRepairSupport,
            CodePatchKernel codePatchKernel
    ) {
        this.patchPayloadRepairSupport = patchPayloadRepairSupport;
        this.codePatchKernel = codePatchKernel;
    }

    PatchApplyResult apply(
            EmbeddedTargetedRewriteRequest request,
            EmbeddedPatchKind patchKind,
            String currentContent,
            EditUnit unit,
            String generated
    ) {
        String normalizedPayload = patchPayloadRepairSupport.normalizeGeneratedPayload(
                patchKind.syntheticPath(request.relativePath()),
                generated
        );
        ExactReplaceEdit edit = patchPayloadRepairSupport.readStructuredPayload(
                request.relativePath(),
                unit,
                normalizedPayload,
                ExactReplaceEdit.class,
                request.eventJournal()
        );
        return patchKind.applyPatch(codePatchKernel, request.relativePath(), currentContent, edit);
    }
}
