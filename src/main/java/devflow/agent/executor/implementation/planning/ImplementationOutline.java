package devflow.agent.executor.implementation.planning;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

/**
 * implementation planning 的第一层稳定产物。
 *
 * <p>outline 只声明：
 * 1. 子任务怎么拆；
 * 2. 每个子任务承担哪些能力；
 * 3. 每个子任务拥有哪组目标文件。
 *
 * <p>它不承载具体 {@link FileChange}，避免模型在第一轮就一次性吐完整大 JSON。
 */
public record ImplementationOutline(
        String summary,
        List<ImplementationOutlineSubtask> subtasks
) {
}
