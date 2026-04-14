package devflow.agent.executor.implementation.toolloop;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import devflow.agent.executor.implementation.state.ImplementationStateSnapshot;
/**
 * tool result replacement 的线程级状态。
 *
 * <p>语义对齐 Claude：
 * 1. seenIds 表示该 tool result 已经进入过模型上下文；
 * 2. replacements 表示该 tool result 已被落盘并替换成引用消息；
 * 3. resume 后继续沿用，避免同一结果反复重写和反复替换。
 */
public final class ToolLoopResultReplacementState {

    private final LinkedHashSet<String> seenIds = new LinkedHashSet<>();
    private final LinkedHashMap<String, String> replacements = new LinkedHashMap<>();

    public ToolLoopResultReplacementState() {
    }

    public ToolLoopResultReplacementState(
            List<String> seenIds,
            List<ImplementationStateSnapshot.ToolResultReplacementEntry> replacements
    ) {
        if (seenIds != null) {
            for (String seenId : seenIds) {
                if (seenId != null && !seenId.isBlank()) {
                    this.seenIds.add(seenId);
                }
            }
        }
        if (replacements != null) {
            for (ImplementationStateSnapshot.ToolResultReplacementEntry replacement : replacements) {
                if (replacement == null || replacement.toolUseId() == null || replacement.toolUseId().isBlank()) {
                    continue;
                }
                this.replacements.put(replacement.toolUseId(), replacement.replacement() == null ? "" : replacement.replacement());
            }
        }
    }

    public boolean seen(String toolUseId) {
        return toolUseId != null && seenIds.contains(toolUseId);
    }

    public void markSeen(String toolUseId) {
        if (toolUseId != null && !toolUseId.isBlank()) {
            seenIds.add(toolUseId);
        }
    }

    public String replacement(String toolUseId) {
        return toolUseId == null ? null : replacements.get(toolUseId);
    }

    public void recordReplacement(String toolUseId, String replacementContent) {
        if (toolUseId == null || toolUseId.isBlank()) {
            return;
        }
        seenIds.add(toolUseId);
        if (replacementContent != null && !replacementContent.isBlank()) {
            replacements.put(toolUseId, replacementContent);
        }
    }

    public List<String> snapshotSeenIds() {
        return List.copyOf(seenIds);
    }

    public List<ImplementationStateSnapshot.ToolResultReplacementEntry> snapshotReplacements() {
        List<ImplementationStateSnapshot.ToolResultReplacementEntry> snapshot = new ArrayList<>();
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            snapshot.add(new ImplementationStateSnapshot.ToolResultReplacementEntry(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(snapshot);
    }
}
