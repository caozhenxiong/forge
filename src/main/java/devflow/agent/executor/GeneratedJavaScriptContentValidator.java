package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 负责 JavaScript 语法级确定性校验。
 */
final class GeneratedJavaScriptContentValidator {

    private static final Duration VALIDATION_TIMEOUT = Duration.ofMinutes(1);
    private static final int VERIFICATION_SUMMARY_MAX_CHARS = 400;

    private final FileProjectWorkspace workspace;
    private final GeneratedJavaScriptStructureValidator structureValidator;

    GeneratedJavaScriptContentValidator(FileProjectWorkspace workspace) {
        this.workspace = workspace;
        this.structureValidator = new GeneratedJavaScriptStructureValidator();
    }

    GeneratedContentValidationFailure validate(Path projectPath, Path relativePath, String content) {
        GeneratedContentValidationFailure structuralFailure = structureValidator.validate(content);
        if (structuralFailure != null) {
            return structuralFailure;
        }
        String lastFailure = null;
        for (String suffix : ProjectPathSupport.javaScriptValidationSuffixes(relativePath)) {
            try {
                Path tempFile = Files.createTempFile("devflow-generated-", suffix);
                try {
                    Files.writeString(tempFile, content);
                    var result = workspace.runCommand(projectPath, List.of("node", "--check", tempFile.toString()), VALIDATION_TIMEOUT);
                    if (result.exitCode() == 0) {
                        return null;
                    }
                    lastFailure = PlaceholderValues.truncateMiddle(result.stderr(), VERIFICATION_SUMMARY_MAX_CHARS);
                } finally {
                    Files.deleteIfExists(tempFile);
                }
            } catch (Exception exception) {
                return new GeneratedContentValidationFailure(
                        GeneratedContentValidationCode.VALIDATION_EXCEPTION,
                        "JavaScript 校验异常: " + exception.getMessage()
                );
            }
        }
        return new GeneratedContentValidationFailure(
                GeneratedContentValidationCode.JAVASCRIPT_CHECK_FAILED,
                "JavaScript 语法检查失败: " + PlaceholderValues.orMachineUnknown(lastFailure)
        );
    }
}
