package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmGenerateRequest;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;

import java.nio.file.Path;

/**
 * 只修复当前候选内容的语法/结构，不重新生成业务逻辑。
 */
public final class SyntaxRepairTurn {

    private static final String SYSTEM_PROMPT = """
            你是语法修复器。
            任务：
            1. 只修复给定内容的语法/结构错误，使其通过本地解析。
            2. 不要新增功能，不要扩大编辑范围，不要修改无关符号。
            3. 不要新增当前 allowedSymbols 之外的任何顶层函数、类、变量或常量声明。
            4. 保持文件其余内容尽可能不变。
            5. 直接返回修复后的完整内容，不要输出解释。
            """;

    private final LlmProvider llmProvider;
    private final PatchRepairSettings settings;

    public SyntaxRepairTurn(LlmProvider llmProvider, PatchRepairSettings settings) {
        this.llmProvider = llmProvider;
        this.settings = settings;
    }

    public String repair(Path relativePath, EditUnit unit, String content, String evidence) {
        String userPrompt = """
                文件：%s
                编辑单元：%s
                允许符号：%s
                错误证据：%s

                只修复下面内容的语法/结构错误，不要重新设计功能：

                %s
                """.formatted(
                relativePath,
                unit.label(),
                unit.allowedSymbols().isEmpty() ? "无（append-only）" : String.join(", ", unit.allowedSymbols()),
                evidence == null || evidence.isBlank() ? "未知" : evidence,
                content == null ? "" : content
        );
        return llmProvider.generate(LlmGenerateRequest.workingPrompt(
                SYSTEM_PROMPT,
                userPrompt,
                LlmOptions.outputBudgetRatio(settings.syntaxRepairOutputRatio()),
                ModelRole.REPAIR
        ));
    }
}
