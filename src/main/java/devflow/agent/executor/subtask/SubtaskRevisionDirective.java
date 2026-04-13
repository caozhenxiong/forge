package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
/**
 * 子任务级验证返回的结构化修订指令。
 *
 * <p>这层只负责把“下一轮执行约束应该怎么改”显式传回执行状态，
 * 不再要求执行器从 retry prose 中反推 edit scope 或 patch 入口。
 */
public record SubtaskRevisionDirective(
        List<FileChange> retryChanges,
        DeliveryMode nextDeliveryMode
) {

    public static SubtaskRevisionDirective empty() {
        return new SubtaskRevisionDirective(List.of(), null);
    }

    public static SubtaskRevisionDirective retry(List<FileChange> retryChanges) {
        return new SubtaskRevisionDirective(retryChanges == null ? List.of() : List.copyOf(retryChanges), null);
    }

    public static SubtaskRevisionDirective patch(List<FileChange> retryChanges) {
        return new SubtaskRevisionDirective(
                retryChanges == null ? List.of() : List.copyOf(retryChanges),
                DeliveryMode.PATCH
        );
    }

    public boolean active() {
        return (retryChanges != null && !retryChanges.isEmpty()) || nextDeliveryMode != null;
    }
}
