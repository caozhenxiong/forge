package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.protocol.ToolResultPayload;
import devflow.agent.protocol.ToolResultsArtifactPayload;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.quality.QualityLedger;
import java.util.List;

/**
 * 负责测试执行记录的 markdown 渲染。
 */
final class TestExecutionArtifactRenderer {

    String render(
            CollectedTestEvidence evidence,
            UiRuntimeContract runtimeContract,
            ExperienceFailureDisposition disposition,
            DocumentLanguage language
    ) {
        SelfCheckResult selfCheck = evidence.selfCheck();
        ArchitectIntegrationCheckResult architectCheck = evidence.architectCheck();
        RuntimeSnapshot runtimeSnapshot = evidence.runtimeSnapshot();
        List<TestCaseResult> caseResults = evidence.caseResults();
        List<ToolResult> toolResults = evidence.toolResults();
        CoverageLedger coverageLedger = evidence.coverageLedger();
        QualityLedger qualityLedger = evidence.qualityLedger();
        StringBuilder builder = new StringBuilder();
        builder.append(StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.TOOL_RESULTS,
                new ToolResultsArtifactPayload(toolResults.stream().map(this::toPayload).toList())
        )).append("\n\n");
        builder.append(StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.QUALITY_LEDGER,
                qualityLedger
        )).append("\n\n");
        builder.append(StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.UI_RUNTIME_CONTRACT,
                runtimeContract == null ? UiRuntimeContract.empty() : runtimeContract
        )).append("\n\n");
        builder.append(StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.EXPERIENCE_FAILURE_DISPOSITION,
                disposition == null ? ExperienceFailureDisposition.pass() : disposition
        )).append("\n\n");
        builder.append("# ").append(language.choose("测试执行记录", "Test Execution")).append("\n\n");
        builder.append("## ").append(language.choose("自检", "Self-check")).append("\n\n");
        builder.append("- passed: ").append(selfCheck.passed()).append("\n");
        builder.append("- summary: ").append(blank(selfCheck.summary())).append("\n\n");
        builder.append("```text\n").append(trim(selfCheck.details())).append("\n```\n\n");
        builder.append("## ").append(language.choose("整体可运行检查", "Architect Runnable Check")).append("\n\n");
        builder.append("- passed: ").append(architectCheck == null || architectCheck.passed()).append("\n");
        if (architectCheck != null && !architectCheck.passed()) {
            builder.append("- failureReason: ")
                    .append(architectCheck.failureReason() == null ? "" : architectCheck.failureReason().wireValue())
                    .append("\n");
            builder.append("- details: ").append(trim(architectCheck.details()).replace("\n", " | ")).append("\n");
        }
        builder.append("\n");
        builder.append("## ").append(language.choose("运行时快照", "Runtime Snapshot")).append("\n\n");
        builder.append(runtimeSnapshot == null
                ? language.choose("- unavailable\n\n", "- unavailable\n\n")
                : runtimeSnapshot.toMarkdown(language).replaceFirst("^# .+\\n\\n", "") + "\n\n");
        builder.append("## ").append(language.choose("运行时观测契约", "UI Runtime Contract")).append("\n\n");
        builder.append(runtimeContract == null
                ? language.choose("- unavailable\n\n", "- unavailable\n\n")
                : runtimeContract.toMarkdown(language).replaceFirst("^# .+\\n\\n", "") + "\n\n");
        renderToolResults(builder, language, toolResults);
        renderCaseResults(builder, language, caseResults);
        if (coverageLedger != null && !coverageLedger.entries().isEmpty()) {
            builder.append("\n## ").append(language.choose("覆盖账本", "Coverage Ledger")).append("\n\n");
            builder.append(coverageLedger.toMarkdown(language)).append("\n");
        }
        if (disposition != null && !disposition.passed()) {
            builder.append("\n## ").append(language.choose("失败归因", "Failure Disposition")).append("\n\n");
            builder.append("- kind: ").append(disposition.kind()).append("\n");
            builder.append("- summary: ").append(blank(disposition.summary())).append("\n");
            builder.append("- changeRequest: ").append(blank(disposition.changeRequest())).append("\n");
            if (!disposition.failingCaseIds().isEmpty()) {
                builder.append("- failingCaseIds: ").append(String.join(", ", disposition.failingCaseIds())).append("\n");
            }
            if (!disposition.failureCapabilitySurfaces().isEmpty()) {
                builder.append("- failureCapabilitySurfaces: ")
                        .append(String.join(", ", disposition.failureCapabilitySurfaces()))
                        .append("\n");
            }
            if (!disposition.requiredCapabilitySurfaces().isEmpty()) {
                builder.append("- requiredCapabilitySurfaces: ")
                        .append(String.join(", ", disposition.requiredCapabilitySurfaces()))
                        .append("\n");
            }
            builder.append("- evidence: ").append(trim(disposition.evidence()).replace("\n", " | ")).append("\n");
        }
        builder.append("\n");
        return builder.toString();
    }

    private void renderToolResults(StringBuilder builder, DocumentLanguage language, List<ToolResult> toolResults) {
        builder.append("## ").append(language.choose("工具执行结果", "Tool Results")).append("\n\n");
        for (ToolResult toolResult : toolResults) {
            builder.append("- tool=")
                    .append(toolResult.toolName())
                    .append(" | status=")
                    .append(toolResult.status())
                    .append("\n");
            if (toolResult.failureCode() != null) {
                builder.append("  failureCode: ").append(toolResult.failureCode()).append("\n");
            }
            if (!blank(toolResult.evidence()).isBlank()) {
                builder.append("  evidence: ").append(trim(toolResult.evidence()).replace("\n", " | ")).append("\n");
            }
            if (!blank(toolResult.recommendedNextAction()).isBlank()) {
                builder.append("  recommendedNextAction: ").append(toolResult.recommendedNextAction()).append("\n");
            }
        }
        builder.append("\n");
    }

    private void renderCaseResults(StringBuilder builder, DocumentLanguage language, List<TestCaseResult> caseResults) {
        builder.append("## ").append(language.choose("测试用例结果", "Test Case Results")).append("\n\n");
        for (TestCaseResult result : caseResults) {
            builder.append("- ")
                    .append(result.id())
                    .append(" | ")
                    .append(result.title())
                    .append(" | required=")
                    .append(result.required())
                    .append(" | status=")
                    .append(result.status())
                    .append("\n");
            builder.append("  detail: ").append(trim(result.details()).replace("\n", " | ")).append("\n");
            if (!blank(result.failureReason()).isBlank()) {
                builder.append("  failureReason: ").append(result.failureReason()).append("\n");
            }
            if (!blank(result.evidence()).isBlank()) {
                builder.append("  evidence: ").append(trim(result.evidence()).replace("\n", " | ")).append("\n");
            }
        }
    }

    private String trim(String value) {
        return PlaceholderValues.truncateTail(value, 12000);
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private ToolResultPayload toPayload(ToolResult toolResult) {
        return new ToolResultPayload(
                toolResult.toolName() == null ? "" : toolResult.toolName().name(),
                toolResult.status() == null ? "" : toolResult.status().name(),
                toolResult.failureCode() == null ? "" : toolResult.failureCode().name(),
                blank(toolResult.evidence()),
                blank(toolResult.recommendedNextAction())
        );
    }
}
