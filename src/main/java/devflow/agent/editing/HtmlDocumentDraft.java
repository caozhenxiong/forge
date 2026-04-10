package devflow.agent.editing;

public record HtmlDocumentDraft(
        String documentTitle,
        String headHtml,
        String markupHtml,
        String styleCss,
        String scriptJs,
        String bodyAppendHtml
) {
}
