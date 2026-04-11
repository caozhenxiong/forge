package devflow.agent.executor;

import java.util.List;

/**
 * 表示一个子任务在当前阶段尝试中的最终执行结果。
 * 这里只记录子任务级结果，不承担阶段级汇总职责。
 */
record SubtaskExecutionReport(
        Subtask subtask,
        boolean completed,
        List<SubtaskAttemptReport> attempts,
        SubtaskExecutionState executionState
) {
    SubtaskExecutionReport(
            Subtask subtask,
            boolean completed,
            List<SubtaskAttemptReport> attempts
    ) {
        this(subtask, completed, attempts, null);
    }

    String lastVerifierChangeRequest() {
        if (attempts.isEmpty()) {
            return "";
        }
        String value = attempts.get(attempts.size() - 1).review().changeRequest();
        return value == null ? "" : value;
    }

    List<FileChange> effectiveChanges() {
        if (executionState == null) {
            return subtask == null || subtask.changes() == null ? List.of() : List.copyOf(subtask.changes());
        }
        return executionState.effectiveChanges(subtask == null ? List.of() : subtask.changes());
    }
}
