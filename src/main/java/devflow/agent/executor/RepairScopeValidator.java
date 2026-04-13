package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * repair-before-regenerate 的作用域校验器。
 *
 * <p>这层不重新解析 patch operation，而是比较修复前后的顶层目标集合，
 * 防止 syntax repair / model repair 在严格 edit unit 上偷偷引入或删除越界符号。
 */
final class RepairScopeValidator {

    private final PatchContextBuilder patchContextBuilder;

    RepairScopeValidator(PatchContextBuilder patchContextBuilder) {
        this.patchContextBuilder = patchContextBuilder;
    }

    ToolResult verifyCodeFile(Path relativePath, String baselineContent, String repairedContent, EditUnit unit) {
        return verifyRestrictedScope(
                relativePath,
                unit,
                patchContextBuilder.relevantCodeSymbols(relativePath, baselineContent),
                patchContextBuilder.relevantCodeSymbols(relativePath, repairedContent)
        );
    }

    ToolResult verifyInlineScript(Path hostRelativePath, String baselineContent, String repairedContent, EditUnit unit) {
        return verifyRestrictedScope(
                hostRelativePath,
                unit,
                patchContextBuilder.relevantInlineScriptSymbols(hostRelativePath, baselineContent),
                patchContextBuilder.relevantInlineScriptSymbols(hostRelativePath, repairedContent)
        );
    }

    ToolResult verifyInlineStyle(Path hostRelativePath, String baselineContent, String repairedContent, EditUnit unit) {
        return verifyRestrictedScope(
                hostRelativePath,
                unit,
                patchContextBuilder.relevantInlineStyleSymbols(hostRelativePath, baselineContent),
                patchContextBuilder.relevantInlineStyleSymbols(hostRelativePath, repairedContent)
        );
    }

    private ToolResult verifyRestrictedScope(
            Path relativePath,
            EditUnit unit,
            List<String> beforeSymbols,
            List<String> afterSymbols
    ) {
        if (unit == null || !unit.restrictsSymbols()) {
            return ToolResult.success(ToolName.CONTENT_VERIFY);
        }
        Set<String> before = new LinkedHashSet<>(beforeSymbols == null ? List.of() : beforeSymbols);
        Set<String> after = new LinkedHashSet<>(afterSymbols == null ? List.of() : afterSymbols);
        Set<String> allowed = new LinkedHashSet<>(unit.allowedSymbols());

        Set<String> missingAllowed = new LinkedHashSet<>(allowed);
        missingAllowed.removeAll(after);
        if (!missingAllowed.isEmpty()) {
            return ToolResult.failure(
                    ToolName.CONTENT_VERIFY,
                    ToolFailureCode.TARGET_SCOPE_VIOLATION,
                    "repair 后缺失当前 edit unit 允许的目标符号: " + String.join(", ", missingAllowed),
                    "请仅修复当前 allowedSymbols 对应的现有符号体，不要删掉目标符号声明。"
            );
        }

        Set<String> addedOutsideAllowed = new LinkedHashSet<>(after);
        addedOutsideAllowed.removeAll(before);
        addedOutsideAllowed.removeAll(allowed);
        if (!addedOutsideAllowed.isEmpty()) {
            return ToolResult.failure(
                    ToolName.CONTENT_VERIFY,
                    ToolFailureCode.TARGET_SCOPE_VIOLATION,
                    "repair 后新增了越界顶层符号: " + String.join(", ", addedOutsideAllowed),
                    "请不要在 syntax repair 中新增当前 allowedSymbols 之外的顶层符号。"
            );
        }

        Set<String> removedOutsideAllowed = new LinkedHashSet<>(before);
        removedOutsideAllowed.removeAll(after);
        removedOutsideAllowed.removeAll(allowed);
        if (!removedOutsideAllowed.isEmpty()) {
            return ToolResult.failure(
                    ToolName.CONTENT_VERIFY,
                    ToolFailureCode.TARGET_SCOPE_VIOLATION,
                    "repair 后删除了越界顶层符号: " + String.join(", ", removedOutsideAllowed),
                    "请不要在 syntax repair 中删除当前 allowedSymbols 之外的既有顶层符号。"
            );
        }

        return ToolResult.success(ToolName.CONTENT_VERIFY);
    }
}
