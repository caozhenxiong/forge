package devflow.agent.orchestrator;

import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.supervisor.SupervisorDecision;

/**
 * supervisor 指引渲染器。
 *
 * <p>负责把 supervisor 的结构化决策渲染成：
 * 1. 可回写到修订说明中的 directive block；
 * 2. 只保留人类可读叙述的 narrative 版本。
 *
 * <p>这样阶段回流支持类就不再继续内嵌大段 supervisor 文案拼装逻辑。
 */
final class SupervisorGuidanceRenderer {

    String renderDirectiveGuidance(SupervisorDecision supervisorDecision) {
        if (supervisorDecision == null) {
            return "";
        }
        String focus = renderList(supervisorDecision.focus());
        String constraints = renderList(supervisorDecision.constraints());
        String requiredEvidence = renderList(supervisorDecision.requiredEvidence());
        if (focus.isBlank()
                && constraints.isBlank()
                && requiredEvidence.isBlank()
                && nullToEmpty(supervisorDecision.reason()).isBlank()) {
            return "";
        }
        String directiveBlock = ExecutionDirectiveProtocol.renderBlock(
                new ExecutionDirectivePayload(
                        null,
                        false,
                        false,
                        supervisorDecision.deliveryPolicy().mode() == null ? null : supervisorDecision.deliveryPolicy().mode().wireValue(),
                        supervisorDecision.deliveryPolicy().maxFiles(),
                        supervisorDecision.deliveryPolicy().maxSymbols(),
                        supervisorDecision.deliveryPolicy().preferPreciseEditing(),
                        supervisorDecision.deliveryPolicy().forceBacklogSplit(),
                        supervisorDecision.deliveryPolicy().requireVerificationBeforeReview(),
                        supervisorDecision.requiredEvidence(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null,
                        null,
                        null,
                        supervisorDecision.action().name(),
                        supervisorDecision.reason(),
                        supervisorDecision.focus(),
                        supervisorDecision.constraints(),
                        null,
                        null,
                        null,
                        null
                )
        );
        return """
                %s

                决策原因：
                %s

                本轮焦点：
                %s

                本轮约束：
                %s

                %s
                """.formatted(
                directiveBlock,
                nullToEmpty(supervisorDecision.reason()),
                focus.isBlank() ? "- 无" : focus,
                constraints.isBlank() ? "- 无" : constraints,
                requiredEvidence.isBlank() ? "- 无" : requiredEvidence
        ).trim();
    }

    String renderNarrativeGuidance(SupervisorDecision supervisorDecision) {
        if (supervisorDecision == null) {
            return "";
        }
        String focus = renderList(supervisorDecision.focus());
        String constraints = renderList(supervisorDecision.constraints());
        String requiredEvidence = renderList(supervisorDecision.requiredEvidence());
        if (focus.isBlank()
                && constraints.isBlank()
                && requiredEvidence.isBlank()
                && nullToEmpty(supervisorDecision.reason()).isBlank()) {
            return "";
        }
        return """
                决策原因：
                %s

                本轮焦点：
                %s

                本轮约束：
                %s

                必需证据：
                %s
                """.formatted(
                nullToEmpty(supervisorDecision.reason()),
                focus.isBlank() ? "- 无" : focus,
                constraints.isBlank() ? "- 无" : constraints,
                requiredEvidence.isBlank() ? "- 无" : requiredEvidence
        ).trim();
    }

    String mergeActionItems(String actionItems, SupervisorDecision supervisorDecision) {
        StringBuilder builder = new StringBuilder(nullToEmpty(actionItems).trim());
        String supervisorGuidance = renderNarrativeGuidance(supervisorDecision).trim();
        if (!builder.isEmpty()) {
            if (!supervisorGuidance.isBlank()) {
                builder.append("\n\n").append(supervisorGuidance);
            }
            return builder.toString().trim();
        }
        return supervisorGuidance;
    }

    private String renderList(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ").append(value.trim());
        }
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
