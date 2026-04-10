package devflow.agent.executor;

import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 负责 implementation 辅助产物渲染时共用的格式化与协议读取。
 * 这里只处理稳定的 markdown/json 片段拼接，不承载任何流程判断。
 */
final class ImplementationArtifactRenderSupport {

    private ImplementationArtifactRenderSupport() {
    }

    static String renderRepairAlignmentSection(String note, DocumentLanguage language) {
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(note);
        if (!Boolean.TRUE.equals(directives.repairBriefEnforced())) {
            return "";
        }
        return """
                ## %s

                %s
                - %s
                - %s
                - %s
                """.formatted(
                language.choose("Repair Brief 对齐", "Repair Brief Alignment"),
                language.choose("本轮实现处于 repair brief 强约束模式：", "This implementation round is operating under an enforced repair brief:"),
                ArtifactLabels.mustFixFirstCoverageRequirement(language),
                ArtifactLabels.forbiddenDirectionsAvoidRequirement(language),
                language.choose("verifier 将优先检查 Acceptance Checks", "The verifier will prioritize Acceptance Checks")
        );
    }

    static String renderChangeList(List<FileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return "无";
        }
        return changes.stream()
                .map(change -> {
                    String rendered = change.action() + " `" + change.path() + "`";
                    if (change.effectiveEditScope() != FileEditScope.AUTO) {
                        rendered = rendered + " [" + change.effectiveEditScope().name() + "]";
                    }
                    return rendered;
                })
                .collect(Collectors.joining("，"));
    }

    static String renderBulletList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return PlaceholderValues.bulletMachineNone();
        }
        return values.stream()
                .map(value -> "- " + value)
                .collect(Collectors.joining("\n"));
    }

    static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    static String summarizeForVerification(String content, int maxChars) {
        return PlaceholderValues.truncateMiddle(content, maxChars);
    }

    static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
