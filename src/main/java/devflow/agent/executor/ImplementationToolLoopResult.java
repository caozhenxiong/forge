package devflow.agent.executor;

import java.nio.file.Path;
import java.util.List;

record ImplementationToolLoopResult(
        String finalResponse,
        List<Path> touchedPaths,
        List<FileMutationRecord> mutationRecords
) {

    ImplementationToolLoopResult {
        finalResponse = finalResponse == null ? "" : finalResponse;
        touchedPaths = touchedPaths == null ? List.of() : List.copyOf(touchedPaths);
        mutationRecords = mutationRecords == null ? List.of() : List.copyOf(mutationRecords);
    }
}
