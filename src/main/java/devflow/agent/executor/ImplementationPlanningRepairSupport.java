package devflow.agent.executor;

/**
 * 负责 implementation planning 单元级 JSON 修复。
 *
 * <p>当前只允许修两类载荷：
 * 1. outline
 * 2. 单个 subtask detail
 *
 * <p>修复目标只到“把当前单元修成合法 JSON”为止，不负责替代重新规划其他单元。
 */
final class ImplementationPlanningRepairSupport {

    private final LlmProvider llmProvider;

    ImplementationPlanningRepairSupport(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    String repairOutline(String brokenResponse, Exception exception) {
        return repair(
                """
                        {
                          "summary": "字符串",
                          "subtasks": [
                            {
                              "id": "subtask-1",
                              "title": "字符串",
                              "goal": "字符串",
                              "deliveryMode": "SKELETON|INCREMENTAL|PATCH|REWORK",
                              "runnableMilestone": true,
                              "coverageRefs": ["CAP-1"],
                              "ownedCapabilities": ["字符串"],
                              "deferredCapabilities": ["字符串"],
                              "acceptanceCriteria": ["字符串"],
                              "targetPaths": ["index.html"]
                            }
                          ]
                        }
                        """,
                exception,
                brokenResponse
        );
    }

    String repairSubtaskDetail(String brokenResponse, Exception exception) {
        return repair(
                """
                        {
                          "subtaskId": "subtask-1",
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
                        """,
                exception,
                brokenResponse
        );
    }

    private String repair(String schema, Exception exception, String brokenResponse) {
        String system = """
                你是 JSON 修复器。请修复输入中的 implementation planning 载荷，使其成为合法 JSON。
                你必须只返回修复后的 JSON 对象，不要输出任何额外解释。
                非 HTML 文件必须使用 editScope=AUTO、runtimeOwnership=null、hostHtmlPatchRequired=false。
                只有 HTML 入口文件允许声明 runtimeOwnership / hostHtmlPatchRequired。
                保持原有字段语义不变，字段格式必须符合：
                %s
                """.formatted(schema);
        String user = """
                当前 JSON 解析错误：
                %s

                待修复内容：
                %s
                """.formatted(exception == null ? "未知" : exception.getMessage(), brokenResponse);
        return llmProvider.generate(
                system,
                user,
                LlmOptions.outputBudgetRatio(GenerationBudgetProfile.implementationPlanRepairOutputRatio()),
                ModelRole.REPAIR
        );
    }
}
