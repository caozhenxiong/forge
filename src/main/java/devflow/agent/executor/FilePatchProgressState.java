package devflow.agent.executor;

import java.nio.file.Path;
import java.util.List;

/**
 * 文件级 patch 执行进度。
 *
 * <p>用于在子任务 retry / stage resume 时继续消费当前文件的失败单元，
 * 而不是重新从首个 unit 整体重跑。
 */
record FilePatchProgressState(
        Path relativePath,
        String strategyName,
        String workingContent,
        List<EditUnit> pendingUnits
) {
    FilePatchProgressState {
        relativePath = relativePath == null ? null : relativePath.normalize();
        strategyName = strategyName == null ? "" : strategyName;
        workingContent = workingContent == null ? "" : workingContent;
        pendingUnits = pendingUnits == null ? List.of() : List.copyOf(pendingUnits);
    }

    boolean matches(Path path, String strategyName) {
        if (path == null) {
            return false;
        }
        return path.normalize().equals(relativePath)
                && this.strategyName.equals(strategyName == null ? "" : strategyName);
    }

    boolean resumable() {
        return !pendingUnits.isEmpty();
    }
}
