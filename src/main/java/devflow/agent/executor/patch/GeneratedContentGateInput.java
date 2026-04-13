package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;

import devflow.agent.executor.runtime.*;

import java.nio.file.Path;

/**
 * 文件生成结果 gate 的输入。
 *
 * <p>这层只关心“某个候选内容对某个目标文件是否合法”，不关心它是通过哪种编辑策略生成出来的。
 */
public record GeneratedContentGateInput(
        Path projectPath,
        Path relativePath,
        String content,
        HtmlRuntimeOwnershipContract runtimeContract,
        java.util.List<Path> relatedPaths
) {
    public GeneratedContentGateInput(Path projectPath, Path relativePath, String content) {
        this(projectPath, relativePath, content, null, java.util.List.of());
    }

    public GeneratedContentGateInput {
        relatedPaths = relatedPaths == null ? java.util.List.of() : java.util.List.copyOf(relatedPaths);
    }
}
