package devflow.agent.executor;

import devflow.agent.editing.FileStateSnapshot;
import java.nio.file.Path;

/**
 * 只修 exact-replace payload 的语义错误，不重新生成整份代码内容。
 */
final class ExactReplaceSemanticRepairTurn {

    private static final String SYSTEM_PROMPT = """
            你是 exact-replace 语义修复器。
            任务：
            1. 只修复当前 exact-replace JSON payload，使其能在当前文件状态上正确 apply。
            2. 不要扩写功能，不要重做整份文件，不要跳出当前编辑单元。
            3. targetPath 必须与输入给出的 targetPath 完全一致。
            4. baseContentHash 必须与输入给出的 contentHash 完全一致。
            5. oldText 必须从当前文件内容里原样拷贝；如果当前文件非空，不允许返回空 oldText。
            6. newText 必须真正产生变更，不能让 oldText 和 newText 相同。
            7. 直接返回合法 JSON，不要输出解释。
            """;

    private final LlmProvider llmProvider;
    private final PatchRepairSettings settings;

    ExactReplaceSemanticRepairTurn(LlmProvider llmProvider, PatchRepairSettings settings) {
        this.llmProvider = llmProvider;
        this.settings = settings;
    }

    String repair(
            Path relativePath,
            EditUnit unit,
            String currentContent,
            String brokenPayload,
            String evidence
    ) {
        String targetPath = relativePath == null ? "" : relativePath.normalize().toString().replace('\\', '/');
        FileStateSnapshot snapshot = ExactReplacePromptSupport.capture(targetPath, currentContent);
        String userPrompt = """
                文件：%s
                编辑单元：%s
                允许符号：%s
                当前文件状态：
                - targetPath: %s
                - contentHash: %s
                - lineCount: %d

                当前失败证据：
                %s

                失败的 payload：
                %s

                当前文件内容（oldText 必须从这里原样拷贝）：
                %s
                """.formatted(
                relativePath,
                unit == null ? "patch" : unit.label(),
                unit == null || unit.allowedSymbols().isEmpty() ? "无（append-only）" : String.join(", ", unit.allowedSymbols()),
                targetPath,
                snapshot.contentHash(),
                snapshot.lineCount(),
                evidence == null || evidence.isBlank() ? "未知" : evidence,
                brokenPayload == null ? "" : brokenPayload,
                ExactReplacePromptSupport.renderCurrentContent(currentContent)
        );
        return llmProvider.generate(
                SYSTEM_PROMPT,
                userPrompt,
                LlmOptions.outputBudgetRatio(settings.semanticRepairOutputRatio()),
                ModelRole.REPAIR
        );
    }
}
