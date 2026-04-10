package devflow.agent.repair;

import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.review.FixMode;
import org.springframework.stereotype.Component;

@Component
public class RepairAgent {

    public String buildRepairNote(
            FixMode requestedMode,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            java.util.List<String> requiredCapabilitySurfaces,
            RepairBrief repairBrief,
            DocumentLanguage language
    ) {
        FixMode effectiveMode = repairBrief.recommendedMode() == null || repairBrief.recommendedMode() == FixMode.NONE
                ? (requestedMode == null || requestedMode == FixMode.NONE ? FixMode.PATCH : requestedMode)
                : repairBrief.recommendedMode();
        String directiveBlock = ExecutionDirectiveProtocol.renderBlock(
                new ExecutionDirectivePayload(
                        effectiveMode.name(),
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        java.util.List.of(),
                        repairBrief.mustFixFirst(),
                        repairBrief.forbiddenDirections(),
                        repairBrief.acceptanceChecks(),
                        requiredCapabilitySurfaces == null ? java.util.List.of() : requiredCapabilitySurfaces,
                        java.util.List.of(),
                        summary,
                        changeRequest,
                        evidence,
                        actionItems,
                        null,
                        null,
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null,
                        null,
                        null
                )
        );
        return """
                %s

                %s:
                %s

                %s:
                %s

                %s:
                %s

                %s:
                %s

                [MUST_FIX_FIRST]
                %s

                [FORBIDDEN_DIRECTIONS]
                %s

                [ACCEPTANCE_CHECKS]
                %s

                %s

                %s

                %s:
                1. %s
                2. %s
                3. %s
                4. %s
        """.formatted(
                directiveBlock,
                language.choose("上一轮评审摘要", "Previous review summary"),
                blank(summary),
                language.choose("上一轮变更要求", "Previous change request"),
                blank(changeRequest),
                language.choose("上一轮关键证据", "Previous key evidence"),
                blank(evidence),
                language.choose("上一轮建议动作", "Previous suggested actions"),
                blank(actionItems),
                renderTaggedList(repairBrief.mustFixFirst()),
                renderTaggedList(repairBrief.forbiddenDirections()),
                renderTaggedList(repairBrief.acceptanceChecks()),
                language.choose("以下是 DiagnosisAgent 输出的问题摘要，请严格按它进行修复：", "Below is the issue summary from DiagnosisAgent. Follow it strictly during repair:"),
                repairBrief.toMarkdown(language),
                language.choose("修复要求", "Repair Requirements"),
                ArtifactLabels.mustFixFirstCoverageRequirement(language),
                ArtifactLabels.forbiddenDirectionsAvoidRequirement(language),
                language.choose("产出结果必须能支撑 Acceptance Target 和 Acceptance Checks", "The result must support the Acceptance Target and Acceptance Checks"),
                language.choose("如果本轮没有覆盖上述关键项，视为修复未完成", "If this round does not cover the key items above, the repair is incomplete")
        ).trim();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private String renderTaggedList(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> "- " + value.trim())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("-");
    }
}
