package devflow.agent.executor.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * generate 主链使用的四层上下文载体。
 *
 * <p>system prompt 继续承载稳定指令；用户侧上下文统一拆成：
 * durable / working / evidence / trace 四层，避免 provider 再只看到一整段长字符串。
 */
public record LlmPromptContext(
        String durableContext,
        String workingContext,
        String evidenceContext,
        String traceContext
) {

    public LlmPromptContext {
        durableContext = safe(durableContext);
        workingContext = safe(workingContext);
        evidenceContext = safe(evidenceContext);
        traceContext = safe(traceContext);
    }

    public static LlmPromptContext empty() {
        return new LlmPromptContext("", "", "", "");
    }

    public static LlmPromptContext workingOnly(String workingContext) {
        return new LlmPromptContext("", workingContext, "", "");
    }

    public String render() {
        List<PromptSection> sections = nonBlankSections();
        if (sections.isEmpty()) {
            return "";
        }
        if (sections.size() == 1) {
            return sections.getFirst().content();
        }
        StringBuilder builder = new StringBuilder();
        for (PromptSection section : sections) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("## ").append(section.title()).append("\n\n");
            builder.append(section.content().strip());
        }
        return builder.toString();
    }

    public boolean isEmpty() {
        return nonBlankSections().isEmpty();
    }

    private List<PromptSection> nonBlankSections() {
        ArrayList<PromptSection> sections = new ArrayList<>();
        append(sections, "Durable Context", durableContext);
        append(sections, "Working Context", workingContext);
        append(sections, "Evidence Context", evidenceContext);
        append(sections, "Trace Context", traceContext);
        return sections;
    }

    private void append(List<PromptSection> sections, String title, String content) {
        if (content != null && !content.isBlank()) {
            sections.add(new PromptSection(title, content));
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record PromptSection(String title, String content) {
    }
}
