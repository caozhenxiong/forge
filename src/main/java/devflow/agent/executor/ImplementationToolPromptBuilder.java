package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.quality.QualityPlan;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * tool-driven coder prompt 组装器。
 *
 * <p>这里只组装结构化来源，不在 prompt 里塞场景特判。
 */
final class ImplementationToolPromptBuilder {

    String systemPrompt() {
        return """
                你是 Forge 的 implementation coder。
                你必须通过工具完成实现，不要编造文件内容，不要跳过读文件步骤。

                硬约束：
                1. 只能修改当前 subtask 明确列出的 writable files。
                2. 修改已有文件前，必须先用 Read 做完整读取。
                3. 优先用 Edit 做局部改动；创建新文件或整体重写时用 Write。
                4. 删除文件时使用 Delete。
                5. 搜索和找文件优先用 Grep / Glob。
                6. 如果工具返回错误，先在当前 tool loop 内修正，不要直接放弃子任务。
                7. 不允许输出骨架、占位、TODO 或半成品冒充完成。
                8. 完成后再给出一句简洁总结，不要输出大段解释。
                """.trim();
    }

    String userPrompt(
            Path projectPath,
            Subtask subtask,
            TaskPackage taskPackage,
            ContractView contractView,
            QualityPlan qualityPlan,
            String feedback,
            String coderContextMarkdown
    ) {
        List<String> writableFiles = subtask == null || subtask.changes() == null
                ? List.of()
                : subtask.changes().stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .map(change -> projectPath.resolve(change.path()).normalize().toString())
                .distinct()
                .toList();
        return """
                # Current Project Root
                %s

                # Writable Files
                %s

                # Current Subtask
                %s

                # Structured Contracts
                %s

                # Quality Plan
                %s

                # Existing Feedback
                %s

                # Additional Coder Context
                %s

                现在开始实现。优先使用工具查看和修改文件，直到当前子任务满足验收要求。
                """.formatted(
                projectPath,
                writableFiles.isEmpty() ? "- (none)" : writableFiles.stream().map(value -> "- " + value).collect(Collectors.joining("\n")),
                taskPackage == null ? "" : taskPackage.toMarkdown(),
                contractView == null ? "" : contractView.toMarkdown(),
                qualityPlan == null ? "" : qualityPlan.toMarkdown(devflow.agent.i18n.DocumentLanguage.ZH),
                feedback == null || feedback.isBlank() ? "无" : feedback,
                coderContextMarkdown == null || coderContextMarkdown.isBlank() ? "无" : coderContextMarkdown
        ).trim();
    }
}
