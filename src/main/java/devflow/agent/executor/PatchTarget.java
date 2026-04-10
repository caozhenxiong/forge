package devflow.agent.executor;

/**
 * 统一描述 patch 目标。
 */
record PatchTarget(
        PatchTargetKind targetKind,
        String symbolName,
        String symbolType
) {
    boolean fileEnd() {
        return targetKind == PatchTargetKind.FILE_END;
    }
}
