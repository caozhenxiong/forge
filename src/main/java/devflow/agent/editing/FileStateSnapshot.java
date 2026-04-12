package devflow.agent.editing;

import java.nio.file.Path;

/**
 * 当前文件内容快照。
 *
 * <p>编辑内核的唯一文件状态来源。
 * 现有文件的 targeted rewrite 和 full rewrite 都必须绑定这份快照，
 * 避免 continuation / repair 继续消费过期上下文。
 */
public record FileStateSnapshot(
        Path relativePath,
        boolean exists,
        String content,
        String contentHash,
        int lineCount
) {

    public FileStateSnapshot {
        relativePath = relativePath == null ? null : relativePath.normalize();
        content = content == null ? "" : content;
        contentHash = contentHash == null ? "" : contentHash;
    }
}
