package devflow.agent.editing;

public record HtmlPrecisePatch(
        String markupHtml,
        String styleCss,
        String scriptJs,
        String headAppendHtml,
        String bodyAppendHtml
) {
    public boolean hasAnyChange() {
        return markupHtml != null
                || styleCss != null
                || scriptJs != null
                || headAppendHtml != null
                || bodyAppendHtml != null;
    }
}
