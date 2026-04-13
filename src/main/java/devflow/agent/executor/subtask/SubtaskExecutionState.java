package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureException;

import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.supervisor.DeliveryPolicyMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import devflow.agent.executor.implementation.toolloop.ImplementationToolSessionState;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.editing.FileEditAttemptState;
/**
 * 表示单个子任务在运行中的执行策略状态。
 *
 * <p>除了 delivery policy，这里还持有：
 * 1. 文件级 edit attempt state，让当前子任务能从失败 target 继续；
 * 2. 文件级 change override，让结构化 verification 可以直接改写下一轮执行约束；
 * 3. 单一 tool session state，让 retry / continuation 续跑同一份编码现场。
 */
public final class SubtaskExecutionState {

    private final DeliveryMode deliveryMode;
    private final boolean preferPreciseEditing;
    private final LinkedHashMap<Path, FileEditAttemptState> fileProgressByPath;
    private final ArrayList<FileChange> effectiveChanges;
    private final ImplementationToolSessionState toolSessionState;

    public SubtaskExecutionState(DeliveryMode deliveryMode, boolean preferPreciseEditing) {
        this(deliveryMode, preferPreciseEditing, new LinkedHashMap<>(), List.of(), new ImplementationToolSessionState());
    }

    private SubtaskExecutionState(
            DeliveryMode deliveryMode,
            boolean preferPreciseEditing,
            LinkedHashMap<Path, FileEditAttemptState> fileProgressByPath,
            List<FileChange> effectiveChanges,
            ImplementationToolSessionState toolSessionState
    ) {
        this.deliveryMode = deliveryMode;
        this.preferPreciseEditing = preferPreciseEditing;
        this.fileProgressByPath = fileProgressByPath == null ? new LinkedHashMap<>() : fileProgressByPath;
        this.effectiveChanges = new ArrayList<>(normalizeChanges(effectiveChanges));
        this.toolSessionState = toolSessionState == null ? new ImplementationToolSessionState() : toolSessionState;
    }

    public DeliveryMode deliveryMode() {
        return deliveryMode;
    }

    public boolean preferPreciseEditing() {
        return preferPreciseEditing;
    }

    public SubtaskExecutionState withRecoveryPolicy(DeliveryPolicy policy) {
        DeliveryMode nextMode = parseMode(policy.mode(), deliveryMode);
        return new SubtaskExecutionState(
                nextMode,
                policy.preferPreciseEditing(),
                copyProgressMap(),
                copyEffectiveChanges(),
                copyToolSessionState()
        );
    }

    public SubtaskExecutionState copy() {
        return new SubtaskExecutionState(
                deliveryMode,
                preferPreciseEditing,
                copyProgressMap(),
                copyEffectiveChanges(),
                copyToolSessionState()
        );
    }

    public String effectiveExistingContent(Path relativePath, String fallbackContent) {
        FileEditAttemptState progressState = fileEditAttemptState(relativePath);
        if (progressState == null || progressState.workingContent() == null) {
            return fallbackContent == null ? "" : fallbackContent;
        }
        return progressState.workingContent();
    }

    public FileEditAttemptState fileEditAttemptState(Path relativePath) {
        if (relativePath == null) {
            return null;
        }
        return fileProgressByPath.get(relativePath.normalize());
    }

    public void recordEditAttemptState(FileEditAttemptState progressState) {
        if (progressState == null || progressState.relativePath() == null) {
            return;
        }
        fileProgressByPath.put(progressState.relativePath(), progressState);
    }

    public void clearEditAttemptState(Path relativePath) {
        if (relativePath == null) {
            return;
        }
        fileProgressByPath.remove(relativePath.normalize());
    }

    public List<FileEditAttemptState> fileEditAttemptStates() {
        return List.copyOf(fileProgressByPath.values());
    }

    public FileChange effectiveChange(FileChange originalChange) {
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

    public List<FileChange> effectiveChanges(List<FileChange> originalChanges) {
        if (!effectiveChanges.isEmpty()) {
            return List.copyOf(effectiveChanges);
        }
        return normalizeChanges(originalChanges);
    }

    public void setEffectiveChanges(List<FileChange> retryChanges) {
        List<FileChange> normalized = normalizeChanges(retryChanges);
        if (normalized.isEmpty()) {
            return;
        }
        effectiveChanges.clear();
        effectiveChanges.addAll(normalized);
        pruneEditAttemptStatesToActivePaths();
    }

    public SubtaskExecutionState applyRevisionDirective(SubtaskRevisionDirective directive) {
        if (directive == null || !directive.active()) {
            return this;
        }
        DeliveryMode nextMode = directive.nextDeliveryMode() == null ? deliveryMode : directive.nextDeliveryMode();
        SubtaskExecutionState nextState = new SubtaskExecutionState(
                nextMode,
                preferPreciseEditing,
                copyProgressMap(),
                copyEffectiveChanges(),
                copyToolSessionState()
        );
        nextState.setEffectiveChanges(directive.retryChanges());
        return nextState;
    }

    /**
     * 文件级生成失败已经携带了 edit attempt state 时，下一轮必须冻结兄弟文件，
     * 只续跑当前失败文件，避免把局部失败又放大回整子任务重写。
     */
    public void applyFileScopedGenerationFailure(Subtask subtask, GenerationFailureException failure) {
        Path failedPath = resolveFailedPath(failure);
        if (failedPath == null) {
            return;
        }
        List<FileChange> sourceChanges = effectiveChanges.isEmpty()
                ? normalizeChanges(subtask == null ? List.of() : subtask.changes())
                : normalizeChanges(effectiveChanges);
        List<FileChange> failedChanges = sourceChanges.stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .filter(change -> failedPath.equals(Path.of(change.path()).normalize()))
                .toList();
        if (failedChanges.isEmpty()) {
            return;
        }
        setEffectiveChanges(failedChanges);
    }

    public List<FileChange> effectiveChanges() {
        return List.copyOf(effectiveChanges);
    }

    public ImplementationToolSessionState toolSessionState() {
        return toolSessionState;
    }

    public void resetToolLoopTranscript() {
        toolSessionState.clearTranscript();
    }

    public static SubtaskExecutionState restore(
            String deliveryMode,
            boolean preferPreciseEditing,
            List<FileEditAttemptState> fileEditAttemptStates,
            List<FileChange> effectiveChanges,
            ImplementationToolSessionState toolSessionState
    ) {
        DeliveryMode resolvedMode = DeliveryMode.valueOf(deliveryMode);
        LinkedHashMap<Path, FileEditAttemptState> progressByPath = new LinkedHashMap<>();
        if (fileEditAttemptStates != null) {
            for (FileEditAttemptState progressState : fileEditAttemptStates) {
                if (progressState == null || progressState.relativePath() == null) {
                    continue;
                }
                progressByPath.put(progressState.relativePath(), progressState);
            }
        }
        return new SubtaskExecutionState(resolvedMode, preferPreciseEditing, progressByPath, effectiveChanges, toolSessionState);
    }

    private LinkedHashMap<Path, FileEditAttemptState> copyProgressMap() {
        LinkedHashMap<Path, FileEditAttemptState> copy = new LinkedHashMap<>();
        for (Map.Entry<Path, FileEditAttemptState> entry : fileProgressByPath.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private List<FileChange> copyEffectiveChanges() {
        return List.copyOf(effectiveChanges);
    }

    private ImplementationToolSessionState copyToolSessionState() {
        return toolSessionState == null ? new ImplementationToolSessionState() : toolSessionState.copy();
    }

    private DeliveryMode parseMode(DeliveryPolicyMode value, DeliveryMode fallback) {
        if (value == null) {
            return fallback;
        }
        DeliveryMode resolved = value.toDeliveryModeOrNull();
        return resolved == null ? fallback : resolved;
    }

    private void pruneEditAttemptStatesToActivePaths() {
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

    private Path resolveFailedPath(GenerationFailureException failure) {
        if (failure == null) {
            return null;
        }
        FileEditAttemptState editAttemptState = failure.editAttemptState();
        if (editAttemptState != null && editAttemptState.relativePath() != null) {
            return editAttemptState.relativePath().normalize();
        }
        return null;
    }
}
