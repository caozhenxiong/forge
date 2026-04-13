package devflow.agent.executor.implementation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.time.Instant;

/**
 * implementation 阶段的单条事件记录。
 *
 * <p>这层只负责保存“什么时候发生了什么”，让 implementation 相关的过程事件
 * 可以和运行时快照一起发布，而不是只散落在 events.log 里。
 */
public record ImplementationEventEntry(
        Instant timestamp,
        String message
) {
}
