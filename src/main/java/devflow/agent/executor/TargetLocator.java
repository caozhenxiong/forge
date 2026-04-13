package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;

/**
 * 统一定位当前文件里可供 patch 使用的稳定目标。
 *
 * <p>Forge 不应该在各处继续直接调用编辑器去“顺手列符号”，
 * 而是通过这层拿到统一的 patch 目标上下文。
 */
interface TargetLocator {

    PatchTargetContext locate(Path relativePath, String source);
}
