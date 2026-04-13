package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 统一描述 patch 目标。
 */
public record PatchTarget(
        PatchTargetKind targetKind,
        String symbolName,
        String symbolType
) {
    public boolean fileEnd() {
        return targetKind == PatchTargetKind.FILE_END;
    }
}
