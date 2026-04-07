package devflow.agent.editing;

public record CodePreciseOperation(
        CodePreciseAction action,
        String targetSymbol,
        String targetKind,
        String content
) {
}
