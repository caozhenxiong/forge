package devflow.agent.executor;

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
        String system = """
                你是资深工程师。请对现有源码做“符号级精确改写”，不要整文件重写。
                你必须只返回一个 JSON 对象，格式如下：
                {
                  "operations": [
                    {
                      "action": "REPLACE_SYMBOL|REPLACE_SYMBOL_BODY|INSERT_INTO_SYMBOL|APPEND_FILE",
                      "targetSymbol": "目标符号名；APPEND_FILE 时可为 null",
                      "targetKind": "class|interface|enum|record|constructor|method|function|type|variable|rule；APPEND_FILE 时可为 null",
                      "contentLines": ["逐行源码内容"]
                    }
                  ]
                }

                规则：
                1. 只返回 JSON，不要解释，不要 markdown
                2. 不要输出完整文件内容
                3. 必须优先使用 contentLines，不要在 content 字段里放多行源码字符串
                4. REPLACE_SYMBOL 必须提供完整声明
                5. REPLACE_SYMBOL_BODY 只替换现有符号体内部内容；contentLines 只能写函数体/方法体/类体内的实现，不要重复声明、签名、export 或外层花括号
                6. INSERT_INTO_SYMBOL 只在目标符号体内部插入内容
                7. APPEND_FILE 只用于新增顶层符号或补充文件尾部内容
                8. 当前编辑单元给出 allowedSymbols 时，只能修改这些符号，不允许 APPEND_FILE
                9. 当前编辑单元没有 allowedSymbols 时，只能使用 APPEND_FILE 追加顶层骨架
                10. 上一轮反馈只作为“当前文件”的修复参考；如果反馈里提到的文件、符号或问题不属于当前文件符号清单，视为其他文件上下文，不要在当前文件处理
                """;

        if (currentContent == null || currentContent.isBlank()) {
            system = system + """

                    当前文件为空或新建文件：
                    1. 只能使用 APPEND_FILE 追加顶层符号或模块骨架
                    2. 不要输出完整文件内容
                    3. 本轮只建立“可持续增量修改”的最小骨架，不要一次实现完整业务逻辑
                    4. 优先拆成多个独立的顶层符号，每个符号只保留最小可解析 body，便于后续按符号继续补实现
                    5. 不要在本轮写入复杂算法、完整渲染流程或大量状态机细节
                    """;
        }
        if (unit.appendOnly() && unit.appendSymbolBudget() > 0) {
            system = system + """

                    当前 append-only 单元预算：
                    1. 本轮最多追加 %d 个新的顶层符号
                    2. 先建立最小骨架，不要一次塞完整模块
                    3. 如果还需要更多符号，由后续拆分后的 append-only 单元继续追加
                    """.formatted(unit.appendSymbolBudget());
        }
        if (strictBodyOnlyUnit) {
            String strictTargetSymbol = unit.allowedSymbols().getFirst();
            system = system + """

                    当前编辑单元已缩到单个受限符号：
                    1. 只允许返回 1 个 operation
                    2. 禁止 APPEND_FILE
                    3. operation.action 必须是 REPLACE_SYMBOL_BODY
                    4. operation.targetSymbol 必须精确等于 "%s"
                    5. 只补当前符号体内的最小实现，不要补额外 helper、不要扩写文件尾部、不要顺手改其他符号
                    6. 当前单元的目标是“让现有符号变得可用”，不是一次补齐完整模块
                    """.formatted(strictTargetSymbol);
        } else if (restrictedUnit) {
            system = system + """

                    当前编辑单元属于受限符号批次：
                    1. 只能修改 allowedSymbols 中列出的现有符号
                    2. 禁止 APPEND_FILE
                    3. 如确实需要新增 helper，交给 append-only 单元处理，不要在本单元偷带辅助符号
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

                当前文件内容：
                %s
                """.formatted(
                planSummary,
                taskPackageMarkdown,
                coderContextMarkdown == null ? "" : coderContextMarkdown,
                relativePath,
                unit.label(),
                unit.allowedSymbols().isEmpty() ? "(append-only)" : String.join(", ", unit.allowedSymbols()),
                unit.appendOnly() ? Integer.toString(Math.max(1, unit.appendSymbolBudget())) : "(n/a)",
                strictBodyOnlyUnit ? unit.allowedSymbols().getFirst() : "(n/a)",
                reason,
                feedback == null ? "" : feedback,
                describedTargets,
                targetedContext,
                summarizedContent.isBlank() ? PlaceholderValues.machineNewFile() : summarizedContent
        );
        return new PatchGenerationPrompt(system, user);
    }
}
