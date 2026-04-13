package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 测试阶段当前支持的确定性执行工具类型。
 *
 * <p>这里故意保持极小集合：先把“如何选工具”收成稳定策略，
 * 再逐步扩展新的执行器，而不是把判断散落在 TestExecutor 里。
 */
public enum TestExecutionTool {
    PLAYWRIGHT,
    UNAVAILABLE
}
