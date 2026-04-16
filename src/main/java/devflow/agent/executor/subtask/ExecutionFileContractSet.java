package devflow.agent.executor.subtask;

import devflow.agent.executor.FileChange;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 当前 attempt 内的唯一 execution file contract 集合。
 */
public record ExecutionFileContractSet(
        List<ExecutionFileContract> contracts
) {

    public ExecutionFileContractSet {
        if (contracts == null || contracts.isEmpty()) {
            contracts = List.of();
        } else {
            LinkedHashMap<Path, ExecutionFileContract> normalized = new LinkedHashMap<>();
            for (ExecutionFileContract contract : contracts) {
                if (contract == null || contract.relativePath() == null || contract.path().isBlank()) {
                    continue;
                }
                normalized.put(contract.relativePath().normalize(), contract);
            }
            contracts = List.copyOf(normalized.values());
        }
    }

    public static ExecutionFileContractSet empty() {
        return new ExecutionFileContractSet(List.of());
    }

    public boolean isEmpty() {
        return contracts.isEmpty();
    }

    public List<String> ownedFiles() {
        return contracts.stream()
                .map(ExecutionFileContract::path)
                .toList();
    }

    public Set<Path> ownedPaths() {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        for (ExecutionFileContract contract : contracts) {
            if (contract == null || contract.relativePath() == null) {
                continue;
            }
            paths.add(contract.relativePath().normalize());
        }
        return Set.copyOf(paths);
    }

    public List<FileChange> declaredChanges() {
        return contracts.stream()
                .map(ExecutionFileContract::declaredChange)
                .toList();
    }

    public ExecutionFileContract contractFor(Path relativePath) {
        if (relativePath == null) {
            return null;
        }
        Path normalized = relativePath.normalize();
        return contracts.stream()
                .filter(contract -> contract != null && normalized.equals(contract.relativePath()))
                .findFirst()
                .orElse(null);
    }

    public ExecutionFileContractSet scopeToPath(String ownedFile) {
        if (ownedFile == null || ownedFile.isBlank()) {
            return this;
        }
        return scopeToPaths(List.of(Path.of(ownedFile).normalize()));
    }

    public ExecutionFileContractSet scopeToPaths(Collection<Path> allowedPaths) {
        if (allowedPaths == null || allowedPaths.isEmpty() || contracts.isEmpty()) {
            return this;
        }
        LinkedHashSet<Path> normalized = new LinkedHashSet<>();
        for (Path allowedPath : allowedPaths) {
            if (allowedPath != null) {
                normalized.add(allowedPath.normalize());
            }
        }
        return new ExecutionFileContractSet(
                contracts.stream()
                        .filter(contract -> contract != null && normalized.contains(contract.relativePath()))
                        .toList()
        );
    }
}
