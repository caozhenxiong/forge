package devflow.agent.parsing;

import java.util.List;

public record CodeStructureSnapshot(
        SourceLanguage language,
        TreeSitterParseSummary parseSummary,
        List<CodeSymbol> symbols
) {
    public boolean supportsPreciseEditing() {
        return parseSummary.supported() && parseSummary.valid() && symbols != null && !symbols.isEmpty();
    }
}
