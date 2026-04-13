package devflow.agent.executor.tools;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

public record StructuredPatchHunk(
        int oldStart,
        int oldLines,
        int newStart,
        int newLines,
        List<String> lines
) {

    public StructuredPatchHunk {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
