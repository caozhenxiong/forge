package devflow.agent.executor.implementation.toolloop;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * Claude-style read file state。
 *
 * <p>编辑和写入不能再只看内容 hash；必须绑定：
 * 1. 模型最后一次看到的完整/局部内容；
 * 2. 当时的文件时间戳；
 * 3. 是否 partial view。
 */
public record CoderReadFileState(
        String content,
        long timestamp,
        Integer offset,
        Integer limit,
        boolean partialView
) {

    public CoderReadFileState {
        content = content == null ? "" : content;
    }

    public boolean fullView() {
        return !partialView && offset == null && limit == null;
    }
}
