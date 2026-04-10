package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 根据 architect 失败原因生成最小 continuation 计划。
 *
 * <p>这里只有一条主线：
 * continuation 只能沿用上一轮已经确定的结构继续补缺口，
 * 不能再把 architect failure 简化成“把所有碰过的文件都重开一遍”的粗暴 PATCH。
 */
final class ArchitectFailureContinuationPlanner {

    ReusableImplementationState build(
            ImplementationStateSnapshot snapshot,
            List<Subtask> subtasks,
            List<SubtaskExecutionReport> previousReports,
            DocumentLanguage language
    ) {
        ArchitectIntegrationFailureReason failureReason = parseFailureReason(snapshot.architectFailureReason());
        if (failureReason != ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID) {
            return null;
        }
        FileChange htmlEntryChange = resolveHtmlEntryOwnershipChange(subtasks);
        if (htmlEntryChange == null || htmlEntryChange.runtimeOwnership() == null) {
            return null;
        }
        List<FileChange> continuationChanges = new ArrayList<>();
        continuationChanges.add(new FileChange(
                htmlEntryChange.path(),
                ChangeAction.WRITE,
                language.choose("延续上一轮入口 runtimeOwnership，并仅修复宿主接线问题", "Preserve the previous entry runtimeOwnership and only repair host wiring"),
                htmlEntryChange.effectiveEditScope(),
                htmlEntryChange.runtimeOwnership()
        ));
        if (htmlEntryChange.runtimeOwnership() == RuntimeOwnershipMode.EXTERNAL_COMPANION) {
            Path companionRuntimePath = Path.of(htmlEntryChange.path()).normalize();
            companionRuntimePath = devflow.agent.util.ProjectPathSupport.extractedInlineScriptAssetPath(companionRuntimePath);
            continuationChanges.add(new FileChange(
                    companionRuntimePath.toString(),
                    ChangeAction.WRITE,
                    language.choose("延续上一轮 companion runtime 所有权，并补齐入口接线后的代码实现", "Preserve the previous companion runtime ownership and complete the wired runtime implementation"),
                    FileEditScope.AUTO,
                    null
            ));
        }
        Subtask continuationSubtask = new Subtask(
                language.choose("延续修复运行时接线缺口", "Continue repairing runtime wiring"),
                blankIfNull(snapshot.summary()),
                List.of(),
                List.of(),
                List.of(),
                List.of(language.choose("HTML 入口与 runtime ownership/wiring 契约通过", "The HTML runtime ownership/wiring contract passes")),
                true,
                DeliveryMode.PATCH,
                List.copyOf(continuationChanges)
        );
        List<Subtask> resumedPlan = new ArrayList<>(subtasks);
        resumedPlan.add(continuationSubtask);
        return new ReusableImplementationState(
                new ImplementationPlan(blankIfNull(snapshot.summary()), resumedPlan),
                previousReports == null ? List.of() : previousReports,
                null
        );
    }

    private FileChange resolveHtmlEntryOwnershipChange(List<Subtask> subtasks) {
        if (subtasks == null || subtasks.isEmpty()) {
            return null;
        }
        FileChange selected = null;
        for (Subtask subtask : subtasks) {
            if (subtask == null || subtask.changes() == null) {
                continue;
            }
            for (FileChange change : subtask.changes()) {
                if (change == null || change.path() == null || change.path().isBlank()) {
                    continue;
                }
                if (!devflow.agent.util.ProjectPathSupport.isHtml(change.path()) || change.runtimeOwnership() == null) {
                    continue;
                }
                selected = change;
            }
        }
        return selected;
    }

    private ArchitectIntegrationFailureReason parseFailureReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ArchitectIntegrationFailureReason.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
