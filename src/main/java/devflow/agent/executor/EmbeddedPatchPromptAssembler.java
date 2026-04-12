package devflow.agent.executor;

import devflow.agent.editing.FileStateSnapshot;
import java.nio.file.Path;

/**
 * 统一组装宿主内嵌 patch 的生成 prompt。
 *
 * <p>宿主脚本/样式主链共享同一套 prompt 结构，只在 patch kind 和单元约束上有稳定差异。
 * 这层把这些模板从 FileEditCoordinator 中抽离，避免协调器继续维护大段字面量。
 */
final class EmbeddedPatchPromptAssembler {

    private EmbeddedPatchPromptAssembler() {
    }

    static PatchGenerationPrompt assemble(
            Path relativePath,
            String planSummary,
            String taskPackageMarkdown,
            String coderContextMarkdown,
            String reason,
            String feedback,
            String targetedContext,
            EmbeddedPatchKind patchKind,
            EditUnit unit,
            String currentContent,
            String describedTargets,
            String summarizedContent,
            boolean restrictedUnit,
            boolean strictAppendOnlyUnit
    ) {
        FileStateSnapshot fileState = StructuredDiffPromptSupport.capture(patchKind.syntheticPath(relativePath), currentContent);
        String numberedContent = StructuredDiffPromptSupport.renderNumberedContent(currentContent);
        String system = patchKind.baseSystemPrompt();
        if (unit.appendOnly() && unit.appendSymbolBudget() > 0) {
            system = system + patchKind.appendBudgetInstruction(unit);
        }
        if (strictAppendOnlyUnit) {
            system = system + patchKind.strictAppendOnlyInstruction();
        } else if (restrictedUnit) {
            system = system + patchKind.restrictedUnitInstruction();
        }
        String user = """
                总体实现摘要：
                %s

                当前任务包：
                %s

                编码角色上下文切片：
                %s

                来源文件：
                %s

                当前工作集状态：
                - sourceHash: %s
                - lineCount: %s

                当前编辑单元：
                - label: %s
                - allowedSymbols: %s
                - appendBudget: %s

                %s改写原因：
                %s

                上一轮反馈：
                %s

                当前相关文件上下文：
                %s

                当前%s可精确编辑的%s：
                %s

                当前%s内容（带行号）：
                %s
                """.formatted(
                planSummary,
                taskPackageMarkdown,
                coderContextMarkdown == null ? "" : coderContextMarkdown,
                relativePath,
                fileState.contentHash(),
                fileState.lineCount(),
                unit.label(),
                unit.allowedSymbols().isEmpty() ? "(append-only)" : String.join(", ", unit.allowedSymbols()),
                unit.appendOnly() ? Integer.toString(Math.max(1, unit.appendSymbolBudget())) : "(n/a)",
                patchKind.displayName(),
                reason,
                feedback == null ? "" : feedback,
                targetedContext,
                patchKind.contentName(),
                patchKind.targetName(),
                describedTargets,
                patchKind.contentName(),
                numberedContent
        );
        return new PatchGenerationPrompt(system, user);
    }
}
