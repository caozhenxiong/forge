package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

public record ImplementationCompletenessResult(
        boolean passed,
        int placeholderMarkerCount,
        int emptyBehaviorCount,
        List<String> issues,
        List<String> evidence
) {

    public static ImplementationCompletenessResult success() {
        return new ImplementationCompletenessResult(true, 0, 0, List.of(), List.of());
    }

    public static ImplementationCompletenessResult failure(
            int placeholderMarkerCount,
            int emptyBehaviorCount,
            List<String> issues,
            List<String> evidence
    ) {
        return new ImplementationCompletenessResult(
                false,
                Math.max(0, placeholderMarkerCount),
                Math.max(0, emptyBehaviorCount),
                issues == null ? List.of() : List.copyOf(issues),
                evidence == null ? List.of() : List.copyOf(evidence)
        );
    }

    public String summary() {
        if (passed) {
            return "当前交付未检测到显式占位或空实现问题。";
        }
        if (!issues.isEmpty()) {
            return String.join(" ", issues);
        }
        return "当前交付仍包含未填充的占位逻辑或空实现。";
    }

    public String changeRequest() {
        if (passed) {
            return "";
        }
        return "请继续把占位逻辑、TODO、空函数或 no-op 处理补成真实行为，不要让实现停留在可运行壳阶段。";
    }

    public String evidenceMarkdown() {
        if (evidence == null || evidence.isEmpty()) {
            return "";
        }
        return evidence.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> "- " + item.trim())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    public String actionItemsMarkdown() {
        if (passed) {
            return "";
        }
        return """
                - 把显式占位标记和 TODO/FIXME 替换成真实实现或删除。
                - 把空函数、空方法、pass/return-only/no-op 处理补成可运行行为。
                - 确保运行入口、事件接线和核心行为至少有一条真实执行路径。
                """.strip();
    }
}
