package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;
import java.util.List;

/**
 * 文件级编辑协议续跑状态。
 *
 * <p>这层替代旧的 patch-progress：
 * 1. 统一记录当前文件的协议类型与工作内容；
 * 2. attempt / resume 只续跑当前 target，不回卷已完成 target；
 * 3. continuation 不再依赖 patch 语义。
 */
public record FileEditAttemptState(
        Path relativePath,
        String protocolName,
        String strategyName,
        String workingContent,
        String plannedFromHash,
        List<String> completedTargetLabels,
        String currentTargetLabel
) {
    public FileEditAttemptState {
        relativePath = relativePath == null ? null : relativePath.normalize();
        protocolName = protocolName == null ? "" : protocolName;
        strategyName = strategyName == null ? "" : strategyName;
        workingContent = workingContent == null ? "" : workingContent;
        plannedFromHash = plannedFromHash == null ? "" : plannedFromHash;
        completedTargetLabels = completedTargetLabels == null ? List.of() : List.copyOf(completedTargetLabels);
        currentTargetLabel = currentTargetLabel == null ? "" : currentTargetLabel;
    }

    public boolean matches(Path path, String strategyName) {
        if (path == null) {
            return false;
        }
        return path.normalize().equals(relativePath)
                && this.strategyName.equals(strategyName == null ? "" : strategyName);
    }

    public boolean matchesProtocol(Path path, String protocolName) {
        if (path == null) {
            return false;
        }
        return path.normalize().equals(relativePath)
                && this.protocolName.equals(protocolName == null ? "" : protocolName);
    }

    public boolean resumable() {
        return !currentTargetLabel.isBlank();
    }
}
