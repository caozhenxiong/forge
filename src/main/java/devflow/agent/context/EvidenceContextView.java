package devflow.agent.context;

import java.util.List;

/**
 * 四层上下文中的 evidence 层。
 *
 * <p>这层放最近可用于流程决策与语义判断的证据，
 * 例如失败摘要与最近几次失败记录。
 */
public record EvidenceContextView(
        String failureSummary,
        List<FailureDigest> recentFailures
) {

    public EvidenceContextView {
        recentFailures = recentFailures == null ? List.of() : List.copyOf(recentFailures);
    }
}
