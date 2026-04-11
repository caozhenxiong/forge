package devflow.agent.executor;

/**
 * 负责 implementation plan 的 JSON 修复提示词与修复调用。
 *
 * <p>这层只处理“模型返回的 plan 不是合法 JSON”这一类问题，
 * 避免 `ImplementationPlanParser` 同时承担 turn loop 和 repair prompt 维护。
 */
final class ImplementationPlanRepairSupport {

    private final LlmProvider llmProvider;

    ImplementationPlanRepairSupport(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    String repairPlan(String brokenResponse, Exception exception) {
        String system = """
                你是 JSON 修复器。请修复输入中的 implementation plan，使其成为合法 JSON。
                你必须只返回修复后的 JSON 对象，不要输出任何额外解释。
                保持原有字段语义不变，字段格式必须是：
                {
                  "summary": "字符串",
                  "subtasks": [
                    {
                      "title": "字符串",
                      "goal": "字符串",
                      "deliveryMode": "SKELETON|INCREMENTAL|PATCH|REWORK",
                      "runnableMilestone": true,
                      "coverageRefs": ["CAP-1"],
                      "ownedCapabilities": ["字符串"],
                      "deferredCapabilities": ["字符串"],
                      "acceptanceCriteria": ["字符串"],
                      "changes": [
                        {
                          "path": "相对路径",
                          "action": "WRITE|DELETE",
                          "reason": "字符串",
                          "editScope": "AUTO|HOST_HTML_PATCH|INLINE_SCRIPT_PATCH|INLINE_STYLE_PATCH",
                          "runtimeOwnership": "INLINE_HOST|EXTERNAL_COMPANION|null",
                          "hostHtmlPatchRequired": false
                        }
                      ]
                    }
                  ]
                }
                """;
        String user = """
                当前 JSON 解析错误：
                %s

                待修复内容：
                %s
                """.formatted(exception.getMessage(), brokenResponse);
        return llmProvider.generate(
                system,
                user,
                LlmOptions.outputBudgetRatio(GenerationBudgetProfile.implementationPlanRepairOutputRatio()),
                ModelRole.REPAIR
        );
    }
}
