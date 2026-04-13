package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
/**
 * 表示实现计划中的一个可独立验证的子任务。
 * 这里显式区分当前负责能力与后续负责能力，避免 verifier 越界校验。
 */
public record Subtask(
        String title,
        String goal,
        List<String> coverageRefs,
        List<String> ownedCapabilities,
        List<String> deferredCapabilities,
        List<String> acceptanceCriteria,
        boolean runnableMilestone,
        DeliveryMode deliveryMode,
        List<FileChange> changes
) {
}
