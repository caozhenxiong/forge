package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolResult;

import java.util.List;

/**
 * 测试执行阶段的结构化结果。
 *
 * <p>它把 testcase 结果和底层工具结果绑定到同一个快照里，
 * 避免上层流程只看到“用例通过/失败”，却看不到测试工具本身
 * 是否真正执行、是否被跳过、以及失败发生在什么层级。
 */
record TestRunReport(
        List<TestCaseResult> caseResults,
        List<ToolResult> toolResults
) {

    TestRunReport {
        caseResults = caseResults == null ? List.of() : List.copyOf(caseResults);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }
}
