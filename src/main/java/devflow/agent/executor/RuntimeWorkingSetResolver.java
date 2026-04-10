package devflow.agent.executor;

import devflow.agent.context.ExecutionContract;
import devflow.agent.util.ProjectPathSupport;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RuntimeWorkingSetResolver {

    public List<Path> resolveSupplementalPaths(
            ProjectFingerprint fingerprint,
            ExecutionContract executionContract,
            List<Path> changedPaths,
            Path currentPath
    ) {
        if (fingerprint == null || executionContract == null || !executionContract.requiresHtmlEntry() || !fingerprint.hasResolvedHtmlEntry()) {
            return List.of();
        }
        Path entryPath = Path.of(fingerprint.resolvedHtmlEntryPath()).normalize();
        Set<Path> plannedPaths = new LinkedHashSet<>();
        if (changedPaths != null) {
            for (Path changedPath : changedPaths) {
                if (changedPath == null) {
                    continue;
                }
                plannedPaths.add(changedPath.normalize());
            }
        }
        if (currentPath != null) {
            plannedPaths.add(currentPath.normalize());
        }

        Set<Path> supplemental = new LinkedHashSet<>();
        if (touchesRuntimeAsset(plannedPaths) && !plannedPaths.contains(entryPath)) {
            supplemental.add(entryPath);
        }
        if (touchesHtml(plannedPaths, entryPath)) {
            supplemental.addAll(adjacentRuntimeAssets(fingerprint, entryPath, plannedPaths));
        }
        supplemental.removeIf(path -> currentPath != null && currentPath.normalize().equals(path));
        return List.copyOf(supplemental);
    }

    private boolean touchesRuntimeAsset(Set<Path> paths) {
        return paths.stream().anyMatch(path -> ProjectPathSupport.isHtml(path)
                || ProjectPathSupport.isRuntimeScript(path)
                || ProjectPathSupport.isStyle(path));
    }

    private boolean touchesHtml(Set<Path> paths, Path entryPath) {
        return paths.stream().anyMatch(path -> path.normalize().equals(entryPath) || ProjectPathSupport.isHtml(path));
    }

    private List<Path> adjacentRuntimeAssets(ProjectFingerprint fingerprint, Path entryPath, Set<Path> plannedPaths) {
        Path parent = entryPath.getParent() == null ? Path.of("") : entryPath.getParent().normalize();
        return fingerprint.fileNames().stream()
                .filter(path -> path != null && !path.isBlank() && !path.startsWith(".devflow/"))
                .map(Path::of)
                .map(Path::normalize)
                .filter(path -> !path.equals(entryPath))
                .filter(path -> !plannedPaths.contains(path))
                .filter(path -> sameParent(path, parent))
                .filter(path -> ProjectPathSupport.isRuntimeScript(path) || ProjectPathSupport.isStyle(path))
                .sorted(Comparator.comparing(Path::toString))
                .limit(RuntimeWorkingSetPolicy.maxAdjacentRuntimeFiles())
                .toList();
    }

    private boolean sameParent(Path path, Path expectedParent) {
        Path actualParent = path.getParent() == null ? Path.of("") : path.getParent().normalize();
        return actualParent.equals(expectedParent);
    }

}
