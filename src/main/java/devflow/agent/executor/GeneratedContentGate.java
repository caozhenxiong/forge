package devflow.agent.executor;

import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.List;

/**
 * 统一负责“生成后的文件内容是否合法”这类确定性检查。
 *
 * <p>这是 Phase 3 第二批 gate 的最小实现：
 * 1. 不判断语义是否满足需求；
 * 2. 只判断候选内容能否安全落盘；
 * 3. 失败时给出稳定的 code / disposition，供编辑主链决定本地重试、缩小单元或改走其他路径。
 */
class GeneratedContentGate implements DeterministicGate<GeneratedContentGateInput> {

    private static final String MISSING_INPUT_CODE = "GENERATED_CONTENT_MISSING";
    private static final String MISSING_INPUT_MESSAGE = "缺少待校验的生成结果。";

    private final GeneratedTreeSitterValidator treeSitterValidator;
    private final GeneratedJavaScriptContentValidator javaScriptContentValidator;
    private final GeneratedHtmlContentValidator htmlContentValidator;
    private final GeneratedPreciseAnchorValidator preciseAnchorValidator;

    GeneratedContentGate(FileProjectWorkspace workspace, TreeSitterSupport treeSitterSupport) {
        this.treeSitterValidator = new GeneratedTreeSitterValidator(treeSitterSupport);
        this.javaScriptContentValidator = new GeneratedJavaScriptContentValidator(workspace);
        this.htmlContentValidator = new GeneratedHtmlContentValidator(javaScriptContentValidator);
        this.preciseAnchorValidator = new GeneratedPreciseAnchorValidator(new TreeSitterTargetLocator(treeSitterSupport));
    }

    @Override
    public GateReport evaluate(GeneratedContentGateInput input) {
        if (input == null) {
            return GateReport.failure(
                    "生成结果校验失败。",
                    List.of(new GateIssue(
                            MISSING_INPUT_CODE,
                            MISSING_INPUT_MESSAGE,
                            GateFailureDisposition.LOCAL_RETRYABLE
                    ))
            );
        }
        GeneratedContentValidationFailure validationFailure = validateGeneratedContent(
                input.projectPath(),
                input.relativePath(),
                input.content(),
                input.runtimeContract(),
                input.relatedPaths()
        );
        if (validationFailure == null) {
            return GateReport.success();
        }
        return GateReport.failure(
                "生成结果未通过本地确定性校验。",
                List.of(new GateIssue(
                        validationFailure.code().name(),
                        validationFailure.message(),
                        GateFailureDisposition.LOCAL_RETRYABLE
                ))
        );
    }

    GenerationFailureType failureTypeFor(GateReport report) {
        if (report == null || report.passed() || report.issues().isEmpty()) {
            return GenerationFailureType.RESULT_FILE_INVALID;
        }
        return GeneratedContentValidationCode.valueOf(report.issues().get(0).code()).failureType();
    }

    ToolFailureCode toolFailureCodeFor(GateReport report) {
        if (report == null || report.passed() || report.issues().isEmpty()) {
            return ToolFailureCode.GENERATED_CONTENT_INVALID;
        }
        return GeneratedContentValidationCode.valueOf(report.issues().get(0).code()).toolFailureCode();
    }

    String renderFailure(GateReport report) {
        if (report == null || report.passed()) {
            return "";
        }
        if (!report.issues().isEmpty() && !report.issues().get(0).message().isBlank()) {
            return report.issues().get(0).message();
        }
        return report.summary();
    }

    private GeneratedContentValidationFailure validateGeneratedContent(
            Path projectPath,
            Path relativePath,
            String content,
            HtmlRuntimeOwnershipContract runtimeContract,
            List<Path> relatedPaths
    ) {
        if (content == null || content.isBlank()) {
            return new GeneratedContentValidationFailure(GeneratedContentValidationCode.EMPTY_OUTPUT, "输出为空");
        }
        String path = relativePath == null ? "" : relativePath.toString().toLowerCase();
        if (ProjectPathSupport.isJavaScript(path)) {
            GeneratedContentValidationFailure treeSitterFailure = treeSitterValidator.validate(relativePath, content);
            if (treeSitterFailure != null) {
                return treeSitterFailure;
            }
            GeneratedContentValidationFailure javaScriptFailure = javaScriptContentValidator.validate(projectPath, relativePath, content);
            if (javaScriptFailure != null) {
                return javaScriptFailure;
            }
            return preciseAnchorValidator.validate(relativePath, content);
        }
        if (ProjectPathSupport.isHtml(path)) {
            GeneratedContentValidationFailure htmlFailure = htmlContentValidator.validate(
                    projectPath,
                    relativePath,
                    content,
                    runtimeContract,
                    relatedPaths
            );
            if (htmlFailure != null) {
                return htmlFailure;
            }
            GeneratedContentValidationFailure treeSitterFailure = treeSitterValidator.validate(relativePath, content);
            if (treeSitterFailure != null) {
                return treeSitterFailure;
            }
            return null;
        }
        if (ProjectPathSupport.isJava(path)
                || ProjectPathSupport.isTypeScript(path)
                || ProjectPathSupport.isPython(path)
                || ProjectPathSupport.isGo(path)
                || ProjectPathSupport.isStyle(path)) {
            GeneratedContentValidationFailure treeSitterFailure = treeSitterValidator.validate(relativePath, content);
            if (treeSitterFailure != null) {
                return treeSitterFailure;
            }
            return preciseAnchorValidator.validate(relativePath, content);
        }
        return null;
    }
}
