package devflow.agent.supervisor;

import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.review.ReviewResult;

/**
 * 统一渲染 supervisor 层的人类可读产物和策略摘要。
 * 这样 SupervisorAgent 可以更专注于“决策”和“兜底”，而不是继续维护大段字符串模板。
 */
public class SupervisorArtifactRenderer {

    public String renderDecisionArtifact(
            devflow.agent.orchestrator.StageType currentStage,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            SupervisorDecision decision,
            DocumentLanguage language
    ) {
        return """
                # %s

                - currentStage: %s
                - reviewDecision: %s
                - reviewFixMode: %s
                - repeatedIssue: %s
                - action: %s
                - targetStage: %s
                - mode: %s
                - humanRequired: %s
                - reason: %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                - mode: %s
                - maxFiles: %s
                - maxSymbols: %s
                - preferPreciseEditing: %s
                - forceBacklogSplit: %s
                - requireVerificationBeforeReview: %s
                """.formatted(
                language.choose("Supervisor 决策", "Supervisor Decision"),
                currentStage,
                reviewResult.decision(),
                reviewResult.fixMode(),
                repeatedIssue,
                decision.action(),
                PlaceholderValues.orMachineNull(decision.targetStage() == null ? null : decision.targetStage().toString()),
                decision.mode(),
                decision.humanRequired(),
                blank(decision.reason()),
                language.choose("关注点", "Focus"),
                renderList(decision.focus()),
                language.choose("约束", "Constraints"),
                renderList(decision.constraints()),
                ArtifactLabels.requiredEvidence(language),
                renderList(decision.requiredEvidence()),
                language.choose("交付策略", "Delivery Policy"),
                decision.deliveryPolicy().mode(),
                decision.deliveryPolicy().maxFiles(),
                decision.deliveryPolicy().maxSymbols(),
                decision.deliveryPolicy().preferPreciseEditing(),
                decision.deliveryPolicy().forceBacklogSplit(),
                decision.deliveryPolicy().requireVerificationBeforeReview()
        );
    }

    public String renderPolicy(DeliveryPolicy policy) {
        return """
                {mode=%s, maxFiles=%s, maxSymbols=%s, preferPreciseEditing=%s, forceBacklogSplit=%s, requireVerificationBeforeReview=%s}
                """.formatted(
                policy.mode(),
                policy.maxFiles(),
                policy.maxSymbols(),
                policy.preferPreciseEditing(),
                policy.forceBacklogSplit(),
                policy.requireVerificationBeforeReview()
        ).trim();
    }

    private String renderList(java.util.List<String> items) {
        if (items == null || items.isEmpty()) {
            return "- " + PlaceholderValues.machineEmpty();
        }
        StringBuilder builder = new StringBuilder();
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("- ").append(item);
        }
        return builder.isEmpty() ? "- " + PlaceholderValues.machineEmpty() : builder.toString();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
