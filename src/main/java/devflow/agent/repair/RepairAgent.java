package devflow.agent.repair;

import devflow.agent.executor.FileChange;
import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.protocol.FileChangePayload;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RepairAgent {

    public String buildRepairNote(
            FixMode requestedMode,
            ImplementationPatchTarget implementationPatchTarget,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            List<FileChange> overrideChanges,
            List<String> requiredCapabilitySurfaces,
            RepairBrief repairBrief,
            DocumentLanguage language
    ) {
        FixMode effectiveMode = repairBrief.recommendedMode() == null || repairBrief.recommendedMode() == FixMode.NONE
                ? (requestedMode == null || requestedMode == FixMode.NONE ? FixMode.PATCH : requestedMode)
                : repairBrief.recommendedMode();
        String directiveBlock = ExecutionDirectiveProtocol.renderBlock(
                new ExecutionDirectivePayload(
                        effectiveMode.name(),
                        implementationPatchTarget == null ? ImplementationPatchTarget.NONE.name() : implementationPatchTarget.name(),
                        overrideChanges == null ? List.<FileChangePayload>of() : overrideChanges.stream().map(this::toPayload).toList(),
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        repairBrief.mustFixFirst(),
                        repairBrief.forbiddenDirections(),
                        repairBrief.acceptanceChecks(),
                        requiredCapabilitySurfaces == null ? List.of() : requiredCapabilitySurfaces,
                        List.of(),
                        summary,
                        changeRequest,
                        evidence,
                        actionItems,
                        null,
                        null,
                        List.of(),
                        List.of(),
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
                language.choose(
                        "以下是 DiagnosisAgent 输出的问题摘要，仅作为跨轮补充上下文；当前轮 summary / change request / evidence 仍是唯一主目标：",
                        "Below is the DiagnosisAgent summary. Treat it only as cross-attempt supplemental context; the current summary / change request / evidence remain the authoritative target."
                ),
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

    private FileChangePayload toPayload(FileChange change) {
        if (change == null) {
            return null;
        }
        return new FileChangePayload(
                change.path(),
                change.action() == null ? null : change.action().name(),
                blank(change.reason()),
                change.effectiveEditScope().name(),
                change.runtimeOwnership() == null ? null : change.runtimeOwnership().name(),
                change.hostHtmlPatchRequired()
        );
    }
}
