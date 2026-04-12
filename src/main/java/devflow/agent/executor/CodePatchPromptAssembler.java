package devflow.agent.executor;

import devflow.agent.editing.FileStateSnapshot;
import devflow.agent.i18n.PlaceholderValues;
import java.nio.file.Path;

/**
 * 统一组装代码 patch 的生成 prompt。
 *
 * <p>这层只负责模板渲染，不负责决定 patch 单元怎么切、失败如何路由，
 * 让 FileEditCoordinator 可以继续向“编排门面”收敛。
 */
final class CodePatchPromptAssembler {

    private CodePatchPromptAssembler() {
    }

    static PatchGenerationPrompt assemble(
            Path relativePath,
            String planSummary,
            String taskPackageMarkdown,
            String coderContextMarkdown,
            String reason,
            String feedback,
            String describedTargets,
            String targetedContext,
            String summarizedContent,
            String currentContent,
            EditUnit unit,
            boolean restrictedUnit,
            boolean strictBodyOnlyUnit
    ) {
        FileStateSnapshot fileState = StructuredDiffPromptSupport.capture(relativePath, currentContent);
        String numberedContent = StructuredDiffPromptSupport.renderNumberedContent(currentContent);
        String system = """
                你是资深工程师。请对现有源码做基于当前文件状态的结构化 diff 改写。
                这条链是“符号级精确改写”的 diff-first 收口版本，但返回协议已经切到结构化 diff hunk。
                你必须只返回一个 JSON 对象，格式如下：
                {
                  "expectedSourceHash": "必须原样拷贝输入中的 sourceHash",
                  "hunks": [
                    {
                      "sourceStartLine": 1,
                      "beforeLines": ["当前文件里该位置原样存在的逐行内容；纯插入时可为空数组"],
                      "afterLines": ["修改后的逐行内容；纯删除时可为空数组"]
                    }
                  ]
                }

                规则：
                1. 只返回 JSON，不要解释，不要 markdown
                2. `expectedSourceHash` 必须与输入给出的 sourceHash 完全一致
                3. `sourceStartLine` 必须使用 1-based 行号，并严格对应输入中的行号
                4. `beforeLines` 必须与当前文件该位置的真实内容逐行完全一致，不能改写、不能省略
                5. `afterLines` 只写修改后的目标内容，不要带 `+/-/@@`
                6. 不要输出整文件重写；只输出最小必要 hunk
                7. 纯插入用空 `beforeLines`；纯删除用空 `afterLines`
                8. 当前编辑单元给出 allowedSymbols 时，只允许围绕这些符号对应的代码区域产生 hunk
                9. 当前编辑单元没有 allowedSymbols 时，只允许在文件尾追加最小骨架 hunk
                10. 上一轮反馈只作为“当前文件”的修复参考；如果反馈里提到的文件或问题不属于当前文件，就不要在这里处理
                """;

        if (currentContent == null || currentContent.isBlank()) {
            system = system + """

                    当前文件为空或新建文件：
                    1. 只能生成 `sourceStartLine=1` 且 `beforeLines=[]` 的追加 hunk
                    2. 本轮只建立“可持续增量修改”的最小骨架，不要一次实现完整业务逻辑
                    3. 不要在本轮写入复杂算法、完整渲染流程或大量状态机细节
                    """;
        }
        if (unit.appendOnly() && unit.appendSymbolBudget() > 0) {
            system = system + """

                    当前 append-only 单元预算：
                    1. 本轮最多追加 %d 个新的顶层骨架块
                    2. 先建立最小骨架，不要一次塞完整模块
                    3. 如果还需要更多符号，由后续单元继续追加
                    """.formatted(unit.appendSymbolBudget());
        }
        if (strictBodyOnlyUnit) {
            String strictTargetSymbol = unit.allowedSymbols().getFirst();
            system = system + """

                    当前编辑单元已缩到单个受限符号：
                    1. hunk 只能覆盖 "%s" 对应的现有实现区域
                    2. 不要额外追加 helper、不要扩写文件尾部、不要顺手改其他符号
                    3. 当前单元的目标是“让现有符号变得可用”，不是一次补齐完整模块
                    """.formatted(strictTargetSymbol);
        } else if (restrictedUnit) {
            system = system + """

                    当前编辑单元属于受限符号批次：
                    1. 只能修改 allowedSymbols 中列出的现有符号对应区域
                    2. 如确实需要新增 helper，交给 append-only 单元处理，不要在本单元偷带辅助符号
                    """;
        }

        String user = """
                总体实现摘要：
                %s

                当前任务包：
                %s

                编码角色上下文切片：
                %s

                文件路径：
                %s

                当前文件状态：
                - sourceHash: %s
                - lineCount: %s

                当前编辑单元：
                - label: %s
                - allowedSymbols: %s
                - appendBudget: %s
                - strictTargetSymbol: %s

                变更原因：
                %s

                上一轮反馈：
                %s

                当前可精确编辑的符号：
                %s

                当前相关文件上下文：
                %s

                当前文件内容（带行号）：
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
                strictBodyOnlyUnit ? unit.allowedSymbols().getFirst() : "(n/a)",
                reason,
                feedback == null ? "" : feedback,
                describedTargets,
                targetedContext,
                numberedContent.isBlank() ? PlaceholderValues.machineNewFile() : numberedContent
        );
        return new PatchGenerationPrompt(system, user);
    }
}
