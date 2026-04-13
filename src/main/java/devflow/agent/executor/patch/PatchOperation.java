package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

/**
 * 通用 patch 操作。
 *
 * <p>这层只承载：
 * 1. 操作类型；
 * 2. 操作目标；
 * 3. 逐行 payload；
 * 不承载语言专用执行细节。
 */
public record PatchOperation(
        PatchOperationType operationType,
        PatchTarget target,
        List<String> contentLines
) {
    public PatchOperation {
        contentLines = contentLines == null ? List.of() : List.copyOf(contentLines);
    }
}
