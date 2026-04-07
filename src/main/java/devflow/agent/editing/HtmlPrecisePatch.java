package devflow.agent.editing;

public record HtmlPrecisePatch(
        String markupHtml,
        String styleCss,
        String scriptJs
) {
    public boolean hasAnyChange() {
        return markupHtml != null || styleCss != null || scriptJs != null;
    }
}
