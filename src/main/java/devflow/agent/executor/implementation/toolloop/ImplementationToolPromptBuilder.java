package devflow.agent.executor.implementation.toolloop;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.quality.QualityPlan;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.TaskPackage;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.editing.FileEditScope;
/**
 * tool-driven coder prompt 组装器。
 *
 * <p>这里只组装结构化来源，不在 prompt 里塞场景特判。
 */
public final class ImplementationToolPromptBuilder {

    public String systemPrompt() {
        return """
                你是 Forge 的 implementation coder。
                你必须通过工具完成实现，不要编造文件内容，不要跳过读文件步骤。

                硬约束：
                1. 只能修改当前 subtask 明确列出的 writable files。
                2. 只能引入两类本地依赖路径：当前 writable files，或项目里已经存在的本地资产。
                3. 修改已有文件前，必须先用 Read 做完整读取。
                4. 优先用 Edit 做局部改动；Write 默认只用于新文件。只有显式 REWORK 时，现有文件才允许整文件重写。
                5. 删除文件时使用 Delete。
                6. 搜索和找文件优先用 Grep / Glob。
                7. Bash 只有在当前工具列表里可见时才能使用；repair mode 下不要把 Bash 当作回退路径。
                8. 如果 Bash 被拒绝，收窄命令或改用 Read / Edit / Delete，不要原样重试。
                9. 如果工具返回错误，先在当前 tool loop 内修正，不要直接放弃子任务。
                10. 不允许输出骨架、占位、TODO 或半成品冒充完成。
                11. 完成后再给出一句简洁总结，不要输出大段解释。
                """.trim();
    }

    public String userPrompt(
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

                # Current File Contracts
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
                renderFileContracts(projectPath, subtask),
                taskPackage == null ? "" : taskPackage.toMarkdown(),
                contractView == null ? "" : contractView.toMarkdown(),
                qualityPlan == null ? "" : qualityPlan.toMarkdown(devflow.agent.i18n.DocumentLanguage.ZH),
                feedback == null || feedback.isBlank() ? "无" : feedback,
                coderContextMarkdown == null || coderContextMarkdown.isBlank() ? "无" : coderContextMarkdown
        ).trim();
    }

    public String continuationPrompt(String feedback) {
        return """
                继续当前子任务，不要重新开始整个实现。
                保留已有 tool loop 状态，只修复当前反馈指出的问题。

                最新反馈：
                %s
                """.formatted(feedback == null ? "" : feedback.trim()).trim();
    }

    private String renderFileContracts(Path projectPath, Subtask subtask) {
        if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
            return "- (none)";
        }
        List<FileChange> changes = subtask.changes().stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .toList();
        if (changes.isEmpty()) {
            return "- (none)";
        }
        return changes.stream()
                .map(change -> renderFileContract(projectPath, change, changes))
                .collect(Collectors.joining("\n"));
    }

    private String renderFileContract(Path projectPath, FileChange change, List<FileChange> changes) {
        Path relativePath = Path.of(change.path()).normalize();
        StringBuilder builder = new StringBuilder("- ")
                .append(projectPath.resolve(relativePath).normalize());
        builder.append(" | action=").append(change.action());
        builder.append(" | editScope=").append(change.effectiveEditScope());
        if (change.runtimeOwnership() != null) {
            builder.append(" | runtimeOwnership=").append(change.runtimeOwnership());
        }
        List<String> declaredRuntimeRoots = declaredRuntimeRoots(relativePath, changes);
        if (!declaredRuntimeRoots.isEmpty()) {
            builder.append(" | declaredRuntimeRoots=").append(String.join(", ", declaredRuntimeRoots));
        }
        if (change.hostHtmlPatchRequired() || change.effectiveEditScope() == FileEditScope.HOST_HTML_PATCH) {
            builder.append(" | constraint=仅修宿主 HTML 结构与接线，不要把主运行时代码内联回宿主页面");
        }
        return builder.toString();
    }

    private List<String> declaredRuntimeRoots(Path htmlEntryPath, List<FileChange> changes) {
        if (htmlEntryPath == null || !devflow.agent.util.ProjectPathSupport.isHtml(htmlEntryPath) || changes == null || changes.isEmpty()) {
            return List.of();
        }
        Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        ArrayList<String> roots = new ArrayList<>();
        for (FileChange change : changes) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            Path candidate = Path.of(change.path()).normalize();
            if (!devflow.agent.util.ProjectPathSupport.isRuntimeScript(candidate)) {
                continue;
            }
            Path candidateParent = candidate.getParent() == null ? Path.of("") : candidate.getParent().normalize();
            if (!htmlParent.toString().isBlank() && !candidateParent.equals(htmlParent) && !candidateParent.startsWith(htmlParent)) {
                continue;
            }
            roots.add(candidate.toString().replace('\\', '/'));
        }
        return List.copyOf(roots);
    }
}
