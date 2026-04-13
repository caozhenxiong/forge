package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;
import java.util.List;

/**
 * 单文件 patch 执行计划。
 *
 * <p>这层先把“当前文件要按哪些最小 patch 单元执行”显式化，
 * 再交给协调器顺序 apply / verify / retry。这样后续可以逐步把
 * 当前的 edit-unit 主链继续演进到更彻底的 patch-first 内核。
 */
record PatchPlan(
        Path targetPath,
        String strategyName,
        List<EditUnit> units
) {
    PatchPlan {
        units = units == null ? List.of() : List.copyOf(units);
    }
}
