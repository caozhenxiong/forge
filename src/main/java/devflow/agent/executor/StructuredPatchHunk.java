package devflow.agent.executor;

import java.util.List;

record StructuredPatchHunk(
        int oldStart,
        int oldLines,
        int newStart,
        int newLines,
        List<String> lines
) {

    StructuredPatchHunk {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
