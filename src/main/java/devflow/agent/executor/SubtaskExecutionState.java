package devflow.agent.executor;

import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.supervisor.DeliveryPolicyMode;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表示单个子任务在运行中的执行策略状态。
 *
 * <p>除了 delivery policy，这里还持有文件级 patch progress，
 * 让当前子任务在 retry/resume 时能从失败单元继续，而不是重新从首个 unit 开始。
 */
final class SubtaskExecutionState {

    private final DeliveryMode deliveryMode;
    private final boolean preferPreciseEditing;
    private final LinkedHashMap<Path, FilePatchProgressState> fileProgressByPath;

    SubtaskExecutionState(DeliveryMode deliveryMode, boolean preferPreciseEditing) {
        this(deliveryMode, preferPreciseEditing, new LinkedHashMap<>());
    }

    private SubtaskExecutionState(
            DeliveryMode deliveryMode,
            boolean preferPreciseEditing,
            LinkedHashMap<Path, FilePatchProgressState> fileProgressByPath
    ) {
        this.deliveryMode = deliveryMode;
        this.preferPreciseEditing = preferPreciseEditing;
        this.fileProgressByPath = fileProgressByPath == null ? new LinkedHashMap<>() : fileProgressByPath;
    }

    DeliveryMode deliveryMode() {
        return deliveryMode;
    }

    boolean preferPreciseEditing() {
        return preferPreciseEditing;
    }

    SubtaskExecutionState withRecoveryPolicy(DeliveryPolicy policy) {
        DeliveryMode nextMode = parseMode(policy.mode(), deliveryMode);
        return new SubtaskExecutionState(nextMode, policy.preferPreciseEditing(), copyProgressMap());
    }

    SubtaskExecutionState copy() {
        return new SubtaskExecutionState(deliveryMode, preferPreciseEditing, copyProgressMap());
    }

    String effectiveExistingContent(Path relativePath, String fallbackContent) {
        FilePatchProgressState progressState = filePatchProgress(relativePath);
        if (progressState == null || progressState.workingContent() == null) {
            return fallbackContent == null ? "" : fallbackContent;
        }
        return progressState.workingContent();
    }

    FilePatchProgressState filePatchProgress(Path relativePath) {
        if (relativePath == null) {
            return null;
        }
        return fileProgressByPath.get(relativePath.normalize());
    }

    void recordPatchProgress(FilePatchProgressState progressState) {
        if (progressState == null || progressState.relativePath() == null) {
            return;
        }
        fileProgressByPath.put(progressState.relativePath(), progressState);
    }

    void clearPatchProgress(Path relativePath) {
        if (relativePath == null) {
            return;
        }
        fileProgressByPath.remove(relativePath.normalize());
    }

    List<FilePatchProgressState> filePatchProgressStates() {
        return List.copyOf(fileProgressByPath.values());
    }

    static SubtaskExecutionState restore(
            String deliveryMode,
            boolean preferPreciseEditing,
            List<FilePatchProgressState> filePatchProgressStates
    ) {
        DeliveryMode resolvedMode = DeliveryMode.valueOf(deliveryMode);
        LinkedHashMap<Path, FilePatchProgressState> progressByPath = new LinkedHashMap<>();
        if (filePatchProgressStates != null) {
            for (FilePatchProgressState progressState : filePatchProgressStates) {
                if (progressState == null || progressState.relativePath() == null) {
                    continue;
                }
                progressByPath.put(progressState.relativePath(), progressState);
            }
        }
        return new SubtaskExecutionState(resolvedMode, preferPreciseEditing, progressByPath);
    }

    private LinkedHashMap<Path, FilePatchProgressState> copyProgressMap() {
        LinkedHashMap<Path, FilePatchProgressState> copy = new LinkedHashMap<>();
        for (Map.Entry<Path, FilePatchProgressState> entry : fileProgressByPath.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private DeliveryMode parseMode(DeliveryPolicyMode value, DeliveryMode fallback) {
        if (value == null) {
            return fallback;
        }
        DeliveryMode resolved = value.toDeliveryModeOrNull();
        return resolved == null ? fallback : resolved;
    }
}
