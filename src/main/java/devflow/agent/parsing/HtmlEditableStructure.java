package devflow.agent.parsing;

public record HtmlEditableStructure(
        TreeSitterParseSummary parseSummary,
        ByteRange headInnerRange,
        ByteRange bodyInnerRange,
        ByteRange appRootInnerRange,
        ByteRange appStyleInnerRange,
        ByteRange appScriptInnerRange
) {
    public boolean supportsPreciseEditing() {
        return parseSummary.valid() && bodyInnerRange != null && appRootInnerRange != null;
    }
}
