package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import devflow.agent.parsing.TreeSitterParseSummary;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;

/**
 * patch apply 之后的本地校验器。
 *
 * <p>这层把“写完之后如何验证”从 patch kernel 里抽出来，
 * 便于后续给不同语言、不同宿主嵌入场景接入统一校验入口。
 */
public final class PatchVerifier {

    private final TreeSitterSupport treeSitterSupport;
    private final GeneratedContentGate generatedContentGate;
    private final GeneratedJavaScriptStructureValidator javaScriptStructureValidator = new GeneratedJavaScriptStructureValidator();

    public PatchVerifier(TreeSitterSupport treeSitterSupport, GeneratedContentGate generatedContentGate) {
        this.treeSitterSupport = treeSitterSupport;
        this.generatedContentGate = generatedContentGate;
    }

    public ToolResult verifyInlineScript(Path hostRelativePath, String scriptContent) {
        GeneratedContentValidationFailure structuralFailure = javaScriptStructureValidator.validate(scriptContent);
        if (structuralFailure != null) {
            return ToolResult.failure(
                    ToolName.CONTENT_VERIFY,
                    ToolFailureCode.JAVASCRIPT_STRUCTURE_INVALID,
                    structuralFailure.message(),
                    "请继续只修复当前 patch 单元允许的脚本结构，不要引入重复声明包装。"
            );
        }
        Path syntheticPath = ProjectPathSupport.inlineScriptSyntheticPath(hostRelativePath);
        TreeSitterParseSummary scriptSummary = treeSitterSupport.analyze(syntheticPath, scriptContent);
        if (scriptSummary.valid()) {
            return ToolResult.success(ToolName.CONTENT_VERIFY);
        }
        return ToolResult.failure(
                ToolName.CONTENT_VERIFY,
                ToolFailureCode.SYNTAX_INVALID,
                "内联脚本工作集未通过 tree-sitter 解析",
                "请继续只改当前 patch 单元允许的符号，并保持脚本可解析。"
        );
    }

    public ToolResult verifyInlineStyle(Path hostRelativePath, String styleContent) {
        Path syntheticPath = ProjectPathSupport.inlineStyleSyntheticPath(hostRelativePath);
        TreeSitterParseSummary styleSummary = treeSitterSupport.analyze(syntheticPath, styleContent);
        if (styleSummary.valid()) {
            return ToolResult.success(ToolName.TREE_SITTER_VERIFY);
        }
        return ToolResult.failure(
                ToolName.TREE_SITTER_VERIFY,
                ToolFailureCode.SYNTAX_INVALID,
                "内联样式工作集未通过 tree-sitter 解析",
                "请继续只改当前 patch 单元允许的样式规则，并保持样式可解析。"
        );
    }

    public ToolResult verifyCodeFile(Path projectPath, Path relativePath, String mergedContent) {
        GateReport validationReport = generatedContentGate.evaluate(new GeneratedContentGateInput(projectPath, relativePath, mergedContent));
        if (validationReport.passed()) {
            return ToolResult.success(ToolName.CONTENT_VERIFY);
        }
        return ToolResult.failure(
                ToolName.CONTENT_VERIFY,
                generatedContentGate.toolFailureCodeFor(validationReport),
                generatedContentGate.renderFailure(validationReport),
                "请继续只修改当前 patch 单元允许的符号，并保持本地内容校验通过。"
        );
    }
}
