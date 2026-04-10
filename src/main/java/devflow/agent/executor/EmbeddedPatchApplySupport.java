package devflow.agent.executor;

import devflow.agent.editing.CodePrecisePatch;

/**
 * 统一维护宿主内嵌 patch 的 decode / validate / apply。
 */
final class EmbeddedPatchApplySupport {

    private final CodePatchProtocolAdapter codePatchProtocolAdapter;
    private final PatchUnitScopeValidator patchUnitScopeValidator;
    private final CodePatchKernel codePatchKernel;
    private final PatchPayloadRepairSupport patchPayloadRepairSupport;

    EmbeddedPatchApplySupport(
            PatchPayloadRepairSupport patchPayloadRepairSupport,
            CodePatchProtocolAdapter codePatchProtocolAdapter,
            PatchUnitScopeValidator patchUnitScopeValidator,
            CodePatchKernel codePatchKernel
    ) {
        this.patchPayloadRepairSupport = patchPayloadRepairSupport;
        this.codePatchProtocolAdapter = codePatchProtocolAdapter;
        this.patchUnitScopeValidator = patchUnitScopeValidator;
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
        CodePrecisePatch patch = patchPayloadRepairSupport.readStructuredPayload(
                request.relativePath(),
                unit,
                normalizedPayload,
                CodePrecisePatch.class,
                request.eventJournal()
        );
        patchUnitScopeValidator.validate(
                request.relativePath(),
                unit,
                codePatchProtocolAdapter.toOperations(patch)
        );
        return patchKind.applyPatch(codePatchKernel, request.relativePath(), currentContent, patch);
    }
}
