package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;

/**
 * 结构性改道时附带生成的额外文件。
 *
 * <p>当前先用于“宿主 HTML + 外提脚本文件”这类场景，
 * 后续样式外提、模板拆分等也可复用这层结构。
 */
public record GeneratedAuxiliaryWrite(
        Path relativePath,
        String content
) {
}
