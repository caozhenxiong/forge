package devflow.agent.parsing;

public record CodeSymbol(
        String name,
        String kind,
        ByteRange replaceRange,
        ByteRange bodyInnerRange
) {
    public boolean supportsInsertion() {
        return bodyInnerRange != null && bodyInnerRange.isValid();
    }
}
