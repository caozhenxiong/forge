package devflow.agent.editing;

import java.util.List;

public record CodePrecisePatch(
        List<CodePreciseOperation> operations
) {
    public boolean hasAnyOperation() {
        return operations != null && !operations.isEmpty();
    }
}
