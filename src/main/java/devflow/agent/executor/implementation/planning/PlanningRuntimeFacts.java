package devflow.agent.executor.implementation.planning;

import devflow.agent.executor.runtime.HtmlRuntimeOwnershipContract;
import java.nio.file.Path;
import java.util.List;

/**
 * planning 阶段唯一允许消费的宿主入口运行时事实。
 */
public record PlanningRuntimeFacts(
        Path htmlEntryPath,
        HtmlRuntimeOwnershipContract runtimeContract
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
    }

    public static PlanningRuntimeFacts empty() {
        return new PlanningRuntimeFacts(null, null);
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
}
