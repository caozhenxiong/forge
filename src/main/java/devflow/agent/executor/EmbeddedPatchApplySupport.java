package devflow.agent.executor;

import devflow.agent.editing.StructuredDiffPatch;

/**
 * 统一维护宿主内嵌 patch 的 decode / validate / apply。
 */
final class EmbeddedPatchApplySupport {

    private final CodePatchKernel codePatchKernel;
    private final PatchPayloadRepairSupport patchPayloadRepairSupport;

    EmbeddedPatchApplySupport(
            PatchPayloadRepairSupport patchPayloadRepairSupport,
            CodePatchKernel codePatchKernel
    ) {
        this.patchPayloadRepairSupport = patchPayloadRepairSupport;
        this.codePatchKernel = codePatchKernel;
    }

    PatchApplyResult apply(
            EmbeddedPatchRequest request,
            EmbeddedPatchKind patchKind,
            String currentContent,
            EditUnit unit,
            String generated
    ) {
        String normalizedPayload = patchPayloadRepairSupport.normalizeGeneratedPayload(
                patchKind.syntheticPath(request.relativePath()),
                generated
        );
        StructuredDiffPatch patch = patchPayloadRepairSupport.readStructuredPayload(
                request.relativePath(),
                unit,
                normalizedPayload,
                StructuredDiffPatch.class,
                request.eventJournal()
        );
        return patchKind.applyPatch(codePatchKernel, request.relativePath(), currentContent, patch);
    }
}
