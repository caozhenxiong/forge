package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责 implementation 阶段的整体 gate 判断。
 * 这个类只关心“计划是否完成、阶段是否可推进”，不参与代码生成与文件写入。
 */
class ImplementationStageGate {

    /**
     * 汇总当前 implementation 尝试的整体完成状态。
     */
    ImplementationStageStatus summarizeStageStatus(
            ImplementationPlan plan,
            List<SubtaskExecutionReport> reports,
            boolean architectCheckPassed
    ) {
        int plannedSubtasks = plan == null || plan.subtasks() == null ? 0 : plan.subtasks().size();
        int executedSubtasks = Math.min(plannedSubtasks, reports == null ? 0 : reports.size());
        int completedSubtasks = 0;
        if (reports != null) {
            for (int index = 0; index < executedSubtasks; index++) {
                if (reports.get(index) != null && reports.get(index).completed()) {
                    completedSubtasks++;
                }
            }
        }
        List<String> incompleteSubtasks = new ArrayList<>();
        if (plan != null && plan.subtasks() != null) {
            for (int index = 0; index < plan.subtasks().size(); index++) {
                if (index >= executedSubtasks) {
                    incompleteSubtasks.add(plan.subtasks().get(index).title());
                    continue;
                }
                SubtaskExecutionReport report = reports.get(index);
                if (report == null || !report.completed()) {
                    incompleteSubtasks.add(plan.subtasks().get(index).title());
                }
            }
        }
        boolean planCompleted = plannedSubtasks > 0
                && executedSubtasks == plannedSubtasks
                && completedSubtasks == plannedSubtasks;
        boolean stageReady = planCompleted && architectCheckPassed;
        BlockingStageDisposition blockingDisposition = blockingDisposition(reports);
        return new ImplementationStageStatus(
                plannedSubtasks,
                executedSubtasks,
                completedSubtasks,
                planCompleted,
                architectCheckPassed,
                stageReady,
                List.copyOf(incompleteSubtasks),
                blockingDisposition == null ? ImplementationContinuationMode.CONTINUE_SUBTASKS : ImplementationContinuationMode.BLOCK_STAGE,
                blockingDisposition == null ? "" : blockingDisposition.summary(),
                blockingDisposition == null ? "" : blockingDisposition.changeRequest(),
                blockingDisposition == null ? "" : blockingDisposition.evidence(),
                blockingDisposition == null ? "" : blockingDisposition.actionItems(),
                blockingDisposition == null ? ImplementationPatchTarget.NONE : blockingDisposition.implementationPatchTarget(),
                blockingDisposition == null ? ReviewReasonCode.NONE : blockingDisposition.reasonCode()
        );
    }

    /**
     * 当 architect 级整体检查失败时，追加一个统一的阶段级失败报告。
     * 这样后续 renderer 和 reviewer 可以用稳定结构读取失败原因，而不需要关心检查来源。
     */
    List<SubtaskExecutionReport> appendArchitectCheckFailure(
            List<SubtaskExecutionReport> reports,
            ArchitectIntegrationCheckResult architectCheckResult,
            DocumentLanguage language
    ) {
        List<SubtaskExecutionReport> extended = new ArrayList<>(reports);
        Subtask architectCheckSubtask = new Subtask(
                language.choose("架构师整体可运行检查", "Architect Runnable Check"),
                language.choose("验证当前交付物满足最小可运行契约", "Verify that the current deliverable satisfies the minimum runnable contract"),
                List.of(),
                List.of(
                        language.choose("整体交付满足 execution contract", "The overall deliverable satisfies the execution contract")
                ),
                List.of(),
                List.of(
                        language.choose("存在可启动入口", "A launchable entry exists"),
                        language.choose("满足最小可运行表面", "The minimum runnable surface is present")
                ),
                true,
                DeliveryMode.PATCH,
                List.of()
        );
        ReviewResult review = new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                language.choose("当前交付物未满足最小可运行契约", "The current deliverable does not satisfy the minimum runnable contract"),
                architectCheckResult.details(),
                architectCheckResult.details(),
                language.choose("补齐入口或运行表面，使交付物满足 execution contract。", "Add the missing entry or runnable surface so the deliverable satisfies the execution contract."),
                architectCheckResult == null
                        ? ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                        : architectCheckResult.implementationPatchTarget()
        );
        SubtaskAttemptReport attemptReport = SubtaskAttemptReport.fromVerification(
                1,
                new SelfCheckResult(false, language.choose("架构师整体检查未通过", "Architect runnable check failed"), architectCheckResult.details()),
                List.of(),
                review
        );
        extended.add(new SubtaskExecutionReport(architectCheckSubtask, false, List.of(attemptReport)));
        return extended;
    }

    private BlockingStageDisposition blockingDisposition(List<SubtaskExecutionReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        for (SubtaskExecutionReport report : reports) {
            if (report == null || report.attempts() == null || report.attempts().isEmpty()) {
                continue;
            }
            SubtaskAttemptReport latest = report.attempts().get(report.attempts().size() - 1);
            ReviewResult review = latest.review();
            if (review == null || review.revisionRoute() != devflow.agent.review.ReviewRevisionRoute.REQUEST_HUMAN) {
                continue;
            }
            String subtaskTitle = report.subtask() == null ? "" : report.subtask().title();
            String evidencePrefix = subtaskTitle == null || subtaskTitle.isBlank()
                    ? ""
                    : "blockedSubtask=" + subtaskTitle + "\n";
            return new BlockingStageDisposition(
                    review.summary(),
                    review.changeRequest(),
                    evidencePrefix + blank(review.evidence()),
                    review.actionItems(),
                    review.implementationPatchTarget(),
                    review.reasonCode()
            );
        }
        return null;
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private record BlockingStageDisposition(
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            ImplementationPatchTarget implementationPatchTarget,
            ReviewReasonCode reasonCode
    ) {
    }
}
