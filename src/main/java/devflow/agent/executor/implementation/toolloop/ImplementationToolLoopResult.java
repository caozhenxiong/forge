package devflow.agent.executor.implementation.toolloop;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;
import java.util.List;

import devflow.agent.executor.implementation.toolloop.FileMutationRecord;
public record ImplementationToolLoopResult(
        String finalResponse,
        List<Path> touchedPaths,
        List<FileMutationRecord> mutationRecords
) {

    public ImplementationToolLoopResult {
        finalResponse = finalResponse == null ? "" : finalResponse;
        touchedPaths = touchedPaths == null ? List.of() : List.copyOf(touchedPaths);
        mutationRecords = mutationRecords == null ? List.of() : List.copyOf(mutationRecords);
    }
}
