package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.TreeSitterParseSummary;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;

/**
 * 统一封装 tree-sitter 级别的生成内容校验。
 */
final class GeneratedTreeSitterValidator {

    private final TreeSitterSupport treeSitterSupport;

    GeneratedTreeSitterValidator(TreeSitterSupport treeSitterSupport) {
        this.treeSitterSupport = treeSitterSupport;
    }

    GeneratedContentValidationFailure validate(Path relativePath, String content) {
        TreeSitterParseSummary summary = treeSitterSupport.analyze(relativePath, content);
        if (!summary.supported() || summary.valid()) {
            return null;
        }
        return new GeneratedContentValidationFailure(
                GeneratedContentValidationCode.SYNTAX_INVALID,
                summary.describe()
        );
    }
}
