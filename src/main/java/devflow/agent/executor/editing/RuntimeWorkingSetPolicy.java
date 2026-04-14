package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一维护 runtime working set 的补充路径策略。
 *
 * <p>HTML 入口相关文件的补充读取是为了降低局部编辑时的上下文盲区，
 * 这里的阈值属于稳定的运行时策略，不应继续散落在 resolver 实现里。
 */
@ConfigurationProperties(prefix = "devflow.runtime-working-set")
public record RuntimeWorkingSetPolicy(
        int maxAdjacentRuntimeFiles
) {

    private static final int DEFAULT_MAX_ADJACENT_RUNTIME_FILES = 4;

    public RuntimeWorkingSetPolicy() {
        this(DEFAULT_MAX_ADJACENT_RUNTIME_FILES);
    }

    public RuntimeWorkingSetPolicy {
        maxAdjacentRuntimeFiles = maxAdjacentRuntimeFiles > 0
                ? maxAdjacentRuntimeFiles
                : DEFAULT_MAX_ADJACENT_RUNTIME_FILES;
    }
}
