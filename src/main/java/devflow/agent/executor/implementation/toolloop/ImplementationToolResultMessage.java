package devflow.agent.executor.implementation.toolloop;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public record ImplementationToolResultMessage(
        String toolUseId,
        String toolName,
        String content,
        int maxResultSizeChars
) {
}
