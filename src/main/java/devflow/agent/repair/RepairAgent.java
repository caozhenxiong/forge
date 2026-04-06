package devflow.agent.repair;

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
            RepairBrief repairBrief
    ) {
        FixMode effectiveMode = repairBrief.recommendedMode() == null || repairBrief.recommendedMode() == FixMode.NONE
                ? (requestedMode == null || requestedMode == FixMode.NONE ? FixMode.PATCH : requestedMode)
                : repairBrief.recommendedMode();
        return """
                [FIX_MODE=%s]
                [REPAIR_BRIEF_ENFORCED]
                [REPAIR_BRIEF]
                上一轮评审摘要：
                %s

                上一轮变更要求：
                %s

                上一轮关键证据：
                %s

                上一轮建议动作：
                %s

                以下是 DiagnosisAgent 输出的问题摘要，请严格按它进行修复：

                %s

                修复要求：
                1. 必须优先覆盖 Must Fix First
                2. 必须避免 Forbidden Directions
                3. 产出结果必须能支撑 Acceptance Target 和 Acceptance Checks
                4. 如果本轮没有覆盖上述关键项，视为修复未完成
                """.formatted(
                effectiveMode,
                blank(summary),
                blank(changeRequest),
                blank(evidence),
                blank(actionItems),
                repairBrief.toMarkdown()
        ).trim();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
