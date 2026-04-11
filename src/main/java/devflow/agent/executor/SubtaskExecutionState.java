package devflow.agent.executor;

import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.supervisor.DeliveryPolicyMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表示单个子任务在运行中的执行策略状态。
 *
 * <p>除了 delivery policy，这里还持有：
 * 1. 文件级 patch progress，让当前子任务能从失败单元继续；
 * 2. 文件级 change override，让结构化 verification 可以直接改写下一轮执行约束。
 */
final class SubtaskExecutionState {

    private final DeliveryMode deliveryMode;
    private final boolean preferPreciseEditing;
    private final LinkedHashMap<Path, FilePatchProgressState> fileProgressByPath;
    private final ArrayList<FileChange> effectiveChanges;

    SubtaskExecutionState(DeliveryMode deliveryMode, boolean preferPreciseEditing) {
        this(deliveryMode, preferPreciseEditing, new LinkedHashMap<>(), List.of());
    }

    private SubtaskExecutionState(
            DeliveryMode deliveryMode,
            boolean preferPreciseEditing,
            LinkedHashMap<Path, FilePatchProgressState> fileProgressByPath,
            List<FileChange> effectiveChanges
    ) {
        this.deliveryMode = deliveryMode;
        this.preferPreciseEditing = preferPreciseEditing;
        this.fileProgressByPath = fileProgressByPath == null ? new LinkedHashMap<>() : fileProgressByPath;
        this.effectiveChanges = new ArrayList<>(normalizeChanges(effectiveChanges));
    }

    DeliveryMode deliveryMode() {
        return deliveryMode;
    }

    boolean preferPreciseEditing() {
        return preferPreciseEditing;
    }

    SubtaskExecutionState withRecoveryPolicy(DeliveryPolicy policy) {
        DeliveryMode nextMode = parseMode(policy.mode(), deliveryMode);
        return new SubtaskExecutionState(nextMode, policy.preferPreciseEditing(), copyProgressMap(), copyEffectiveChanges());
    }

    SubtaskExecutionState copy() {
        return new SubtaskExecutionState(deliveryMode, preferPreciseEditing, copyProgressMap(), copyEffectiveChanges());
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

    FileChange effectiveChange(FileChange originalChange) {
        if (originalChange == null || originalChange.path() == null || originalChange.path().isBlank()) {
            return originalChange;
        }
        Path relativePath = Path.of(originalChange.path()).normalize();
        return effectiveChanges.stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .filter(change -> relativePath.equals(Path.of(change.path()).normalize()))
                .findFirst()
                .orElse(originalChange);
    }

    List<FileChange> effectiveChanges(List<FileChange> originalChanges) {
        if (!effectiveChanges.isEmpty()) {
            return List.copyOf(effectiveChanges);
        }
        return normalizeChanges(originalChanges);
    }

    void setEffectiveChanges(List<FileChange> retryChanges) {
        List<FileChange> normalized = normalizeChanges(retryChanges);
        if (normalized.isEmpty()) {
            return;
        }
        effectiveChanges.clear();
        effectiveChanges.addAll(normalized);
        prunePatchProgressToActivePaths();
    }

    void applyRevisionDirective(SubtaskRevisionDirective directive) {
        if (directive == null || !directive.active()) {
            return;
        }
        setEffectiveChanges(directive.retryChanges());
    }

    List<FileChange> effectiveChanges() {
        return List.copyOf(effectiveChanges);
    }

    static SubtaskExecutionState restore(
            String deliveryMode,
            boolean preferPreciseEditing,
            List<FilePatchProgressState> filePatchProgressStates,
            List<FileChange> effectiveChanges
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
        return new SubtaskExecutionState(resolvedMode, preferPreciseEditing, progressByPath, effectiveChanges);
    }

    private LinkedHashMap<Path, FilePatchProgressState> copyProgressMap() {
        LinkedHashMap<Path, FilePatchProgressState> copy = new LinkedHashMap<>();
        for (Map.Entry<Path, FilePatchProgressState> entry : fileProgressByPath.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private List<FileChange> copyEffectiveChanges() {
        return List.copyOf(effectiveChanges);
    }

    private DeliveryMode parseMode(DeliveryPolicyMode value, DeliveryMode fallback) {
        if (value == null) {
            return fallback;
        }
        DeliveryMode resolved = value.toDeliveryModeOrNull();
        return resolved == null ? fallback : resolved;
    }

    private void prunePatchProgressToActivePaths() {
        if (effectiveChanges.isEmpty() || fileProgressByPath.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<Path> activePaths = new java.util.LinkedHashSet<>();
        for (FileChange change : effectiveChanges) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            activePaths.add(Path.of(change.path()).normalize());
        }
        fileProgressByPath.entrySet().removeIf(entry -> !activePaths.contains(entry.getKey()));
    }

    private List<FileChange> normalizeChanges(List<FileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Path, FileChange> normalized = new LinkedHashMap<>();
        for (FileChange change : changes) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            normalized.put(Path.of(change.path()).normalize(), change);
        }
        return List.copyOf(normalized.values());
    }
}
