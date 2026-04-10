package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 只修 JSON 载荷，不重新生成业务 patch。
 */
final class ModelJsonRepairTurn {

    private static final String SYSTEM_PROMPT = """
            你是 JSON 载荷修复器。
            任务：
            1. 只修复给定内容，使其成为合法 JSON。
            2. 保持原有 patch 语义，不要扩展编辑范围。
            3. 不要添加解释、Markdown 或代码块。
            4. 直接返回修复后的 JSON 对象。
            5. 如果存在多行源码，必须放进 operations[].contentLines 数组。
            """;

    private final LlmProvider llmProvider;
    private final PatchRepairSettings settings;

    ModelJsonRepairTurn(LlmProvider llmProvider, PatchRepairSettings settings) {
        this.llmProvider = llmProvider;
        this.settings = settings;
    }

    String repair(Path relativePath, EditUnit unit, String payload, String evidence) {
        return repair(
                relativePath,
                unit == null ? "patch" : unit.label(),
                unit == null || unit.allowedSymbols().isEmpty() ? "无（append-only）" : String.join(", ", unit.allowedSymbols()),
                payload,
                evidence
        );
    }

    String repair(Path relativePath, String operationLabel, String payload, String evidence) {
        return repair(relativePath, operationLabel, "无（append-only）", payload, evidence);
    }

    private String repair(Path relativePath, String operationLabel, String allowedSymbols, String payload, String evidence) {
        String userPrompt = """
                文件：%s
                编辑单元：%s
                允许符号：%s
                错误证据：%s

                请只修复下面这段 JSON 载荷，不要重做需求，不要新增解释：

                %s
                """.formatted(
                relativePath,
                operationLabel,
                allowedSymbols,
                evidence == null || evidence.isBlank() ? "未知" : evidence,
                payload == null ? "" : payload
        );
        return llmProvider.generate(
                SYSTEM_PROMPT,
                userPrompt,
                LlmOptions.outputBudgetRatio(settings.jsonRepairOutputRatio()),
                ModelRole.REPAIR
        );
    }
}
