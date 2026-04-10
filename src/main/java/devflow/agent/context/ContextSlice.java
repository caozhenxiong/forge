package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;

/**
 * 某个角色在当前时刻真正应读取的上下文切片。
 * 通过显式把不该读取的层置空，避免后续调用方继续把全量历史直接塞回 prompt。
 */
public record ContextSlice(
        DurableContextView durableContext,
        WorkingContextView workingContext,
        EvidenceContextView evidenceContext,
        TraceContextView traceContext
) {

    public String toMarkdown(DocumentLanguage language) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(language.choose("上下文切片", "Context Slice")).append("\n\n");
        appendSection(builder, language.choose("Durable Context", "Durable Context"), durableContext == null ? "" : renderDurable(language));
        appendSection(builder, language.choose("Working Context", "Working Context"), workingContext == null ? "" : renderWorking(language));
        appendSection(builder, language.choose("Evidence Context", "Evidence Context"), evidenceContext == null ? "" : renderEvidence(language));
        appendSection(builder, language.choose("Trace Context", "Trace Context"), traceContext == null ? "" : renderTrace(language));
        return builder.toString().trim();
    }

    private void appendSection(StringBuilder builder, String title, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        builder.append("## ").append(title).append("\n\n");
        builder.append(content.strip()).append("\n\n");
    }

    private String renderDurable(DocumentLanguage language) {
        return """
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                """.formatted(
                language.choose("目标", "Goal"), blank(durableContext.goal(), language),
                language.choose("约束", "Constraints"), blank(durableContext.constraints(), language),
                language.choose("上游契约摘要", "Upstream Contract Summary"), blank(durableContext.upstreamContractSummary(), language),
                language.choose("结构化契约摘要", "Structured Contract Summary"), blank(durableContext.structuredContractSummary(), language)
        );
    }

    private String renderWorking(DocumentLanguage language) {
        return """
                - %s: %s
                - %s: %s
                - %s: %s
                """.formatted(
                language.choose("当前阶段", "Current Stage"), workingContext.currentStage(),
                language.choose("当前阶段摘要", "Current Stage Summary"), blank(workingContext.currentStageSummary(), language),
                language.choose("工作集摘要", "Working Set Summary"), blank(workingContext.workingSetSummary(), language)
        );
    }

    private String renderEvidence(DocumentLanguage language) {
        return """
                - %s: %s
                - %s: %s
                """.formatted(
                language.choose("失败摘要", "Failure Summary"), blank(evidenceContext.failureSummary(), language),
                language.choose("最近失败数", "Recent Failure Count"), evidenceContext.recentFailures().size()
        );
    }

    private String renderTrace(DocumentLanguage language) {
        return """
                - %s: %s
                - %s: %s
                """.formatted(
                language.choose("当前阶段", "Current Stage"), traceContext.currentStage(),
                language.choose("最近历史摘要", "Recent History Summary"), blank(traceContext.recentHistorySummary(), language)
        );
    }

    private String blank(String value, DocumentLanguage language) {
        return PlaceholderValues.orNone(value, language);
    }
}
