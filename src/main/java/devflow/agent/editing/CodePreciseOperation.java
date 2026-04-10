package devflow.agent.editing;

import java.util.List;

public record CodePreciseOperation(
        CodePreciseAction action,
        String targetSymbol,
        String targetKind,
        String content,
        List<String> contentLines
) {

    public CodePreciseOperation(
            CodePreciseAction action,
            String targetSymbol,
            String targetKind,
            String content
    ) {
        this(action, targetSymbol, targetKind, content, null);
    }
}
