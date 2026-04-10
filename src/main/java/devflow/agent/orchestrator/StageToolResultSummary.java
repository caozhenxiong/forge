package devflow.agent.orchestrator;

import java.util.List;

/**
 * 阶段级工具结果摘要。
 *
 * <p>这层给流程控制消费，避免 `FlowController` 重新接触执行层原始对象，
 * 也避免从 markdown prose 里猜“工具是否真的失败”。
 */
public record StageToolResultSummary(
        boolean blockingFailure,
        int failedToolCount,
        List<String> failedTools,
        List<String> failureCodes,
        String summary,
        String evidence,
        String recommendedAction
) {

    public StageToolResultSummary {
        failedTools = failedTools == null ? List.of() : List.copyOf(failedTools);
        failureCodes = failureCodes == null ? List.of() : List.copyOf(failureCodes);
        summary = summary == null ? "" : summary;
        evidence = evidence == null ? "" : evidence;
        recommendedAction = recommendedAction == null ? "" : recommendedAction;
    }

    public static StageToolResultSummary none() {
        return new StageToolResultSummary(false, 0, List.of(), List.of(), "", "", "");
    }
}
