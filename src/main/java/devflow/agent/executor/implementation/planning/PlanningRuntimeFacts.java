package devflow.agent.executor.implementation.planning;

import devflow.agent.executor.runtime.HtmlRuntimeOwnershipContract;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * planning 阶段唯一允许消费的宿主入口运行时事实。
 */
public record PlanningRuntimeFacts(
        Path htmlEntryPath,
        HtmlRuntimeOwnershipContract runtimeContract,
        List<Path> reachableRuntimePaths,
        List<Path> runtimeRootPaths
) {

    public PlanningRuntimeFacts {
        htmlEntryPath = htmlEntryPath == null ? null : htmlEntryPath.normalize();
        if (runtimeContract != null && runtimeContract.htmlEntryPath() != null) {
            runtimeContract = new HtmlRuntimeOwnershipContract(
                    runtimeContract.htmlEntryPath(),
                    runtimeContract.runtimeOwnership(),
                    runtimeContract.runtimePaths()
            );
        }
        reachableRuntimePaths = normalizePaths(reachableRuntimePaths);
        runtimeRootPaths = normalizePaths(runtimeRootPaths);
    }

    public static PlanningRuntimeFacts empty() {
        return new PlanningRuntimeFacts(null, null, List.of(), List.of());
    }

    public boolean hasResolvedHtmlEntry() {
        return htmlEntryPath != null;
    }

    public boolean matchesHtmlEntry(Path candidate) {
        return htmlEntryPath != null && candidate != null && htmlEntryPath.equals(candidate.normalize());
    }

    public List<Path> wiredRuntimePaths() {
        return runtimeContract == null ? List.of() : runtimeContract.runtimePaths();
    }

    private static List<Path> normalizePaths(List<Path> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Path> normalized = new LinkedHashSet<>();
        for (Path value : values) {
            if (value != null) {
                normalized.add(value.normalize());
            }
        }
        return List.copyOf(normalized);
    }
}
