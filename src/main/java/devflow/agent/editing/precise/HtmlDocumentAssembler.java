package devflow.agent.editing.precise;

import java.nio.file.Path;

public class HtmlDocumentAssembler {

    public String assemble(Path relativePath, HtmlDocumentDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("HTML draft must not be null");
        }
        String title = nonBlank(draft.documentTitle(), fallbackTitle(relativePath));
        String headHtml = trimBlock(draft.headHtml());
        String markupHtml = trimBlock(draft.markupHtml());
        String styleCss = trimBlock(draft.styleCss());
        String scriptJs = trimBlock(draft.scriptJs());
        String bodyAppendHtml = trimBlock(draft.bodyAppendHtml());

        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html lang=\"en\">\n");
        builder.append("<head>\n");
        builder.append("  <meta charset=\"UTF-8\">\n");
        builder.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        builder.append("  <title>").append(escapeHtml(title)).append("</title>\n");
        if (!headHtml.isBlank()) {
            builder.append(indentBlock(headHtml, "  ")).append('\n');
        }
        builder.append("  <style id=\"app-style\">\n");
        if (!styleCss.isBlank()) {
            builder.append(indentBlock(styleCss, "    ")).append('\n');
        }
        builder.append("  </style>\n");
        builder.append("</head>\n");
        builder.append("<body>\n");
        builder.append("  <main id=\"app-root\">\n");
        if (!markupHtml.isBlank()) {
            builder.append(indentBlock(markupHtml, "    ")).append('\n');
        }
        builder.append("  </main>\n");
        if (!bodyAppendHtml.isBlank()) {
            builder.append(indentBlock(bodyAppendHtml, "  ")).append('\n');
        }
        builder.append("  <script id=\"app-script\">\n");
        if (!scriptJs.isBlank()) {
            builder.append(indentBlock(scriptJs, "    ")).append('\n');
        }
        builder.append("  </script>\n");
        builder.append("</body>\n");
        builder.append("</html>\n");
        return builder.toString();
    }

    private String fallbackTitle(Path relativePath) {
        String fileName = relativePath == null || relativePath.getFileName() == null
                ? "App"
                : relativePath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trimBlock(String value) {
        return value == null ? "" : value.strip();
    }

    private String indentBlock(String content, String indent) {
        return content.lines()
                .map(line -> line.isBlank() ? "" : indent + line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
