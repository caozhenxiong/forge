package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 结构性改道时附带生成的额外文件。
 *
 * <p>当前先用于“宿主 HTML + 外提脚本文件”这类场景，
 * 后续样式外提、模板拆分等也可复用这层结构。
 */
record GeneratedAuxiliaryWrite(
        Path relativePath,
        String content
) {
}
