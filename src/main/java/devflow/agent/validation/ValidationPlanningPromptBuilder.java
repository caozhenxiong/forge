package devflow.agent.validation;

import java.util.List;

/**
 * 统一装配验证策略规划提示词，避免 planner 直接维护长提示词文本。
 */
final class ValidationPlanningPromptBuilder {

    String systemPrompt() {
        return """
                你是验证策略规划器。请根据项目特征，从给定 capability 列表中选择一个有序子集作为 self-check 策略。
                你必须只返回 JSON：
                {
                  "summary": "一句话总结",
                  "steps": [
                    {
                      "capability": "枚举值",
                      "reason": "为什么选择该能力",
                      "required": true
                    }
                  ]
                }

                规则：
                1. 只能从提供的 capability 候选列表里选
                2. 优先使用项目已有工具链
                3. 没有构建链路时，退回通用静态 Web 检查
                4. 不要发明新工具，不要输出命令行
                """;
    }

    String userPrompt(ProjectFingerprint fingerprint, List<ValidationStep> candidates) {
        return """
                项目特征：
                %s

                候选 capability：
                %s
                """.formatted(
                String.join("\n", fingerprint.evidence()),
                candidates.stream()
                        .map(step -> "- " + step.capability() + ": " + step.reason())
                        .toList()
        );
    }
}
