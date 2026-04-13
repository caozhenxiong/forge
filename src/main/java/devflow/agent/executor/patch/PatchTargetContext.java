package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.SourceLanguage;
import java.nio.file.Path;
import java.util.List;

/**
 * patch 规划阶段的目标上下文快照。
 *
 * <p>这层只描述“当前文件里有哪些稳定目标可供 patch 使用”，
 * 不承担 patch apply、流程决策或模型调用职责。
 */
public record PatchTargetContext(
        Path relativePath,
        SourceLanguage language,
        boolean preciseEditingSupported,
        List<String> targetNames,
        List<String> insertableTargetNames,
        String targetSummary
) {

    public PatchTargetContext {
        targetNames = targetNames == null ? List.of() : List.copyOf(targetNames);
        insertableTargetNames = insertableTargetNames == null ? List.of() : List.copyOf(insertableTargetNames);
        targetSummary = targetSummary == null ? "" : targetSummary;
    }

    public boolean hasInsertableTargets() {
        return !insertableTargetNames.isEmpty();
    }
}
