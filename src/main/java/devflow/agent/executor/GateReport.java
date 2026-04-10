package devflow.agent.executor;

import java.util.Comparator;
import java.util.List;

/**
 * 确定性 gate 的统一输出。
 *
 * <p>第一版先收最关键的信息：
 * 1. 是否通过
 * 2. 概览摘要
 * 3. 结构化 issue 列表
 *
 * <p>这样 planner / executor / flow controller 可以逐步接入同一类结果对象，
 * 不再各自维护一套布尔值和字符串拼接逻辑。
 */
public record GateReport(
        boolean passed,
        String summary,
        List<GateIssue> issues
) {

    public GateReport {
        summary = summary == null ? "" : summary.trim();
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static GateReport success() {
        return new GateReport(true, "", List.of());
    }

    public static GateReport failure(String summary, List<GateIssue> issues) {
        return new GateReport(false, summary, issues);
    }

    public GateFailureDisposition strongestDisposition() {
        if (issues.isEmpty()) {
            return GateFailureDisposition.LOCAL_RETRYABLE;
        }
        return issues.stream()
                .map(GateIssue::disposition)
                .max(Comparator.comparingInt(this::severity))
                .orElse(GateFailureDisposition.ESCALATE);
    }

    /**
     * 把 gate 结果转成可回灌给 planner 的重试反馈。
     *
     * <p>这里保持最小抽象：gate 只负责输出“失败了什么”，具体补充哪段阶段级指导文案由调用方传入，
     * 避免 GateReport 自己长成又一个阶段特化类。
     */
    public String toRetryFeedback(String footer) {
        if (passed) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (!summary.isBlank()) {
            builder.append(summary);
        }
        for (GateIssue issue : issues) {
            if (issue == null || issue.message().isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("- ").append(issue.message());
        }
        String normalizedFooter = footer == null ? "" : footer.trim();
        if (!normalizedFooter.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(normalizedFooter);
        }
        return builder.toString().trim();
    }

    private int severity(GateFailureDisposition disposition) {
        if (disposition == GateFailureDisposition.LOCAL_RETRYABLE) {
            return 1;
        }
        if (disposition == GateFailureDisposition.REPLAN_CURRENT_STAGE) {
            return 2;
        }
        if (disposition == GateFailureDisposition.ESCALATE) {
            return 3;
        }
        return 4;
    }
}
