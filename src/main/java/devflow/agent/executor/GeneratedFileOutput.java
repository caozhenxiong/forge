package devflow.agent.executor;

import java.util.List;

/**
 * 单文件生成阶段的统一写入计划。
 *
 * <p>主文件内容始终存在；当执行发生结构性改道时，
 * 允许顺带产出一组辅助文件写入，而不是再让协调器
 * 临时散落额外的 side effect。
 */
record GeneratedFileOutput(
        String primaryContent,
        List<GeneratedAuxiliaryWrite> auxiliaryWrites
) {

    GeneratedFileOutput {
        auxiliaryWrites = auxiliaryWrites == null ? List.of() : List.copyOf(auxiliaryWrites);
    }

    static GeneratedFileOutput primaryOnly(String primaryContent) {
        return new GeneratedFileOutput(primaryContent, List.of());
    }
}
