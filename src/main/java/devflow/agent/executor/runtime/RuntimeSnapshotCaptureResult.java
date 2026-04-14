package devflow.agent.executor.runtime;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolResult;

/**
 * 运行时快照抓取结果。
 *
 * <p>它把页面快照本身和工具级结论绑定在一起，避免上层流程只能
 * 看到 `RuntimeSnapshot`，却不知道快照是被跳过、真正抓取成功，
 * 还是在执行器层面就失败了。
 */
public record RuntimeSnapshotCaptureResult(
        RuntimeSnapshot runtimeSnapshot,
        ToolResult toolResult
) {
}
