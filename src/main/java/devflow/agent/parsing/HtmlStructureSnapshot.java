package devflow.agent.parsing;

import java.util.Set;

public record HtmlStructureSnapshot(
        TreeSitterParseSummary parseSummary,
        boolean hasHtmlRoot,
        boolean hasBody,
        boolean hasCanvas,
        int inlineScriptCount,
        Set<String> idSelectors,
        Set<String> buttonSelectors
) {
}
