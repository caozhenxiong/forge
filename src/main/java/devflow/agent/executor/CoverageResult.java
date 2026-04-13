package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

public record CoverageResult(
        boolean passed,
        String summary,
        List<String> issues
) {

    public static CoverageResult success() {
        return new CoverageResult(true, "", List.of());
    }

    public static CoverageResult failure(String summary, List<String> issues) {
        return new CoverageResult(false, summary, issues == null ? List.of() : List.copyOf(issues));
    }

    public String toPlanningFeedback() {
        if (passed) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(summary);
        for (String issue : issues) {
            builder.append("\n- ").append(issue);
        }
        builder.append("""

                请重新规划子任务，并确保：
                1. 子任务集合覆盖执行契约要求的入口与最小可运行表面
                2. 不要只拆内部逻辑模块
                3. 保持小步交付，但第一批交付必须形成可运行表面
                4. 至少有一个子任务必须负责把当前交付物接成可启动、可验证的运行状态
                """);
        return builder.toString().trim();
    }
}
