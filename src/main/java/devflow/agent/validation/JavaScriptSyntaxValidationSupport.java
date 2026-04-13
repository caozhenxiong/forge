package devflow.agent.validation;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import devflow.agent.executor.shell.CommandResult;

import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaScript 语法检查支撑。
 *
 * <p>统一处理：
 * 1. 普通 JS 文件语法检查；
 * 2. HTML 内联脚本语法检查；
 * 3. 按模块制式选择稳定的 `node --check` 校验后缀。
 */
final class JavaScriptSyntaxValidationSupport {

    private final FileProjectWorkspace workspace;

    JavaScriptSyntaxValidationSupport(FileProjectWorkspace workspace) {
        this.workspace = workspace;
    }

    ValidationStepExecution run(Path projectPath, String reason) {
        List<String> checked = new ArrayList<>();
        for (Path relativePath : workspace.listProjectFiles(projectPath)) {
            if (ProjectPathSupport.isJavaScript(relativePath)) {
                ValidationStepExecution execution = checkJavaScriptSyntax(projectPath, relativePath, workspace.readFile(projectPath, relativePath), reason);
                ValidationStepResult result = execution.stepResult();
                if (result.status() == ValidationStatus.FAILED) {
                    return execution;
                }
                checked.add("js syntax ok: " + relativePath);
                continue;
            }
            if (ProjectPathSupport.isHtml(relativePath)) {
                ValidationStepExecution inlineExecution = checkInlineScripts(projectPath, relativePath, workspace.readFile(projectPath, relativePath), reason);
                ValidationStepResult inlineResult = inlineExecution.stepResult();
                if (inlineResult.status() == ValidationStatus.FAILED) {
                    return inlineExecution;
                }
                if (!inlineResult.details().isBlank()) {
                    checked.add(inlineResult.details());
                }
            }
        }
        return new ValidationStepExecution(
                new ValidationStepResult(
                        ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                        ValidationStatus.PASSED,
                        "JavaScript 语法检查通过。",
                        reason + "\n" + (checked.isEmpty() ? "No JavaScript files detected." : String.join("\n", checked))
                ),
                ToolResult.success(ToolName.JAVASCRIPT_SYNTAX_VERIFY)
        );
    }

    private ValidationStepExecution checkInlineScripts(Path projectPath, Path htmlFile, String content, String reason) {
        List<String> checked = new ArrayList<>();
        int index = 0;
        for (String inlineScriptBody : HtmlDocumentInspector.inlineScriptBodies(content)) {
            String scriptBody = inlineScriptBody.trim();
            if (scriptBody.isBlank()) {
                continue;
            }
            index++;
            try {
                String failure = checkJavaScriptSyntaxAcrossSupportedSuffixes(projectPath, ProjectPathSupport.inlineScriptVirtualPath(), scriptBody);
                if (failure != null) {
                    return new ValidationStepExecution(
                            new ValidationStepResult(
                                    ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                                    ValidationStatus.FAILED,
                                    "内联脚本语法检查失败。",
                                    reason + "\nInline script syntax check failed for " + htmlFile + "#" + index + "\n" + failure
                            ),
                            ToolResult.failure(
                                    ToolName.JAVASCRIPT_SYNTAX_VERIFY,
                                    ToolFailureCode.JAVASCRIPT_SYNTAX_INVALID,
                                    failure,
                                    "请先修复当前内联脚本语法，再继续依赖脚本级验证结论。"
                            )
                    );
                }
                checked.add("inline script ok: " + htmlFile + "#" + index);
            } catch (IOException exception) {
                return new ValidationStepExecution(
                        new ValidationStepResult(
                                ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                                ValidationStatus.FAILED,
                                "无法检查内联脚本。",
                                reason + "\n" + exception.getMessage()
                        ),
                        ToolResult.failure(
                                ToolName.JAVASCRIPT_SYNTAX_VERIFY,
                                ToolFailureCode.JAVASCRIPT_SYNTAX_INVALID,
                                exception.getMessage(),
                                "请先修复脚本语法检查环境，再继续执行脚本验证。"
                        )
                );
            }
        }
        return new ValidationStepExecution(
                new ValidationStepResult(
                        ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                        ValidationStatus.PASSED,
                        "内联脚本语法检查通过。",
                        String.join("\n", checked)
                ),
                ToolResult.success(ToolName.JAVASCRIPT_SYNTAX_VERIFY)
        );
    }

    private ValidationStepExecution checkJavaScriptSyntax(Path projectPath, Path relativePath, String content, String reason) {
        try {
            String failure = checkJavaScriptSyntaxAcrossSupportedSuffixes(projectPath, relativePath, content);
            if (failure != null) {
                return new ValidationStepExecution(
                        new ValidationStepResult(
                                ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                                ValidationStatus.FAILED,
                                "JavaScript 语法检查失败。",
                                reason + "\nJS syntax check failed for " + relativePath + "\n" + failure
                        ),
                        ToolResult.failure(
                                ToolName.JAVASCRIPT_SYNTAX_VERIFY,
                                ToolFailureCode.JAVASCRIPT_SYNTAX_INVALID,
                                failure,
                                "请先修复脚本语法错误，再继续依赖 JavaScript 验证。"
                        )
                );
            }
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                            ValidationStatus.PASSED,
                            "JavaScript 语法检查通过。",
                            "js syntax ok: " + relativePath
                    ),
                    ToolResult.success(ToolName.JAVASCRIPT_SYNTAX_VERIFY)
            );
        } catch (IOException exception) {
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK,
                            ValidationStatus.FAILED,
                            "JavaScript 语法检查失败。",
                            reason + "\nJS syntax check setup failed for " + relativePath + "\n" + exception.getMessage()
                    ),
                    ToolResult.failure(
                            ToolName.JAVASCRIPT_SYNTAX_VERIFY,
                            ToolFailureCode.JAVASCRIPT_SYNTAX_INVALID,
                            exception.getMessage(),
                            "请先修复脚本语法检查环境，再继续执行 JavaScript 验证。"
                    )
            );
        }
    }

    private String checkJavaScriptSyntaxAcrossSupportedSuffixes(Path projectPath, Path relativePath, String content) throws IOException {
        String lastFailure = null;
        for (String suffix : ProjectPathSupport.javaScriptValidationSuffixes(relativePath)) {
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("devflow-js-syntax-", suffix);
                Files.writeString(tempFile, content);
                CommandResult result = workspace.runCommand(projectPath, List.of("node", "--check", tempFile.toString()), Duration.ofMinutes(1));
                if (result.exitCode() == 0) {
                    return null;
                }
                lastFailure = trim(result.stderr());
            } finally {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        return PlaceholderValues.orMachineUnknown(lastFailure);
    }

    private String trim(String value) {
        return PlaceholderValues.truncateTail(value, 12000);
    }
}
