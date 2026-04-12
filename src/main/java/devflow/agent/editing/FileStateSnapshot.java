package devflow.agent.editing;

import java.nio.file.Path;

/**
 * 当前文件内容快照。
 *
 * <p>diff-first 主链要求模型补丁明确绑定到当前文件状态，
 * 避免 repair / continuation 继续拿过期上下文改已经变化过的内容。
 */
public record FileStateSnapshot(
        Path relativePath,
        String contentHash,
        int lineCount
) {
}
