package devflow.agent.repair;

import devflow.agent.review.FixMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一生成 diagnosis 失败时的保守 repair brief。
 *
 * <p>这层只负责把最近失败轨迹折叠成稳定的 fallback brief，
 * 避免 DiagnosisAgent 自己继续内联拼接默认文案和 repeated errors。
 */
final class DiagnosisFallbackBriefBuilder {

    RepairBrief build(
            List<DiagnosisAgent.FailureEntry> recentEntries,
            FixMode requestedMode,
            String summary,
            String changeRequest
    ) {
        List<String> repeatedErrors = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        for (DiagnosisAgent.FailureEntry entry : recentEntries) {
            repeatedErrors.add(blank(entry.summary()) + " | " + firstLine(entry.changeRequest()));
            if (entry.evidence() != null && !entry.evidence().isBlank()) {
                evidence.add(firstLine(entry.evidence()));
            }
            if (entry.actionItems() != null && !entry.actionItems().isBlank()) {
                evidence.add(firstLine(entry.actionItems()));
            }
        }
        evidence.add(blank(summary));
        evidence.add(firstLine(changeRequest));
        return new RepairBrief(
                firstLine(summary),
                repeatedErrors,
                "连续多轮出现相同或高度相似的失败，原实现路径没有收敛，需要根据最近失败轨迹做定点修复。",
                List.of(),
                evidence.stream()
                        .filter(item -> item != null && !item.isBlank())
                        .distinct()
                        .toList(),
                requestedModeOrDefault(requestedMode),
                List.of(firstNonBlank(summary, changeRequest)),
                List.of("不要继续沿着已失败的同一路径重复修改"),
                List.of("不要无差别重写整个功能"),
                List.of("修复后同类错误不再重复出现"),
                List.of("验证最近连续失败里反复出现的问题已经消失")
        );
    }

    private String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.strip().lines().findFirst().orElse("").trim();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        if (second != null && !second.isBlank()) {
            return firstLine(second);
        }
        return "优先修复最近连续失败中重复出现的核心问题";
    }

    private FixMode requestedModeOrDefault(FixMode requestedMode) {
        return requestedMode == null || requestedMode == FixMode.NONE ? FixMode.PATCH : requestedMode;
    }
}
