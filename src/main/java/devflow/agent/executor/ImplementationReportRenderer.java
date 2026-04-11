package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.review.ImplementationPatchTarget;
import java.util.List;

/**
 * 负责 implementation 主报告与 repair alignment 的 markdown 渲染。
 * 这个类只面向单一状态源快照，不读取工作区，也不做阶段判断。
 */
final class ImplementationReportRenderer {

    private final ImplementationStageStatusSectionRenderer stageStatusSectionRenderer = new ImplementationStageStatusSectionRenderer();
    private final ImplementationSubtaskBreakdownRenderer subtaskBreakdownRenderer = new ImplementationSubtaskBreakdownRenderer();
    private final ImplementationSubtaskReportRenderer subtaskReportRenderer = new ImplementationSubtaskReportRenderer();

    String renderReport(ImplementationRuntimeSnapshot snapshot) {
        ImplementationPlan plan = snapshot.plan();
        List<SubtaskExecutionReport> reports = snapshot.reports();
        String note = snapshot.note();
        DocumentLanguage language = snapshot.language();
        ImplementationStageStatus stageStatus = snapshot.stageStatus();
        String currentSubtaskTitle = snapshot.currentSubtaskTitle();
        String stageStatusBlock = StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                new ImplementationStageStatusPayload(
                        stageStatus.stageReady(),
                        stageStatus.planCompleted(),
                        stageStatus.architectCheckPassed(),
                        snapshot.architectCheckResult() == null || snapshot.architectCheckResult().failureReason() == null
                                ? ""
                                : snapshot.architectCheckResult().failureReason().name(),
                        snapshot.architectCheckResult() == null ? "" : snapshot.architectCheckResult().details(),
                        snapshot.architectCheckResult() == null
                                ? ImplementationPatchTarget.NONE.name()
                                : snapshot.architectCheckResult().implementationPatchTarget().name(),
                        stageStatus.incompleteSubtasks()
                )
        );
        StringBuilder builder = new StringBuilder("""
                %s

                # %s

                ## %s

                %s

                ## %s

        """.formatted(
                stageStatusBlock,
                language.choose("代码实现", "Implementation"),
                language.choose("实现摘要", "Implementation Summary"),
                plan.summary(),
                ""
        ));
        builder.append(stageStatusSectionRenderer.render(stageStatus, snapshot.architectCheckResult(), language));
        builder.append(subtaskBreakdownRenderer.render(plan, language));

        String repairAlignmentSection = ImplementationArtifactRenderSupport.renderRepairAlignmentSection(note, language);
        if (!repairAlignmentSection.isBlank()) {
            builder.append(repairAlignmentSection).append("\n\n");
        }

        builder.append(subtaskReportRenderer.render(plan, reports, currentSubtaskTitle, language));
        return builder.toString();
    }

    String renderRepairAlignment(
            List<SubtaskExecutionReport> reports,
            String note,
            DeliveryPolicyEnvelope deliveryPolicy,
            DocumentLanguage language
    ) {
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(note);
        if (!Boolean.TRUE.equals(directives.repairBriefEnforced())) {
            return """
                    # %s

                    - status: NOT_APPLICABLE
                    - note: %s
                    """.formatted(
                    language.choose("修复对齐", "Repair Alignment"),
                    language.choose("当前实现不处于 repair brief 强约束模式。", "The current implementation is not operating under an enforced repair brief.")
            );
        }
        List<String> completedSubtasks = reports.stream()
                .filter(SubtaskExecutionReport::completed)
                .map(report -> report.subtask().title())
                .toList();
        String noteSummary = ImplementationArtifactRenderSupport.summarizeForVerification(note, 2600);
        return """
                # %s

                - status: ACTIVE
                - deliveryMode: %s
                - completedSubtasks: %s

                ## %s

                ```text
                %s
                ```

                ## %s

                %s
                """.formatted(
                language.choose("修复对齐", "Repair Alignment"),
                deliveryPolicy.mode(),
                completedSubtasks.isEmpty() ? PlaceholderValues.machineNone() : String.join("，", completedSubtasks),
                language.choose("修复上下文", "Repair Context"),
                noteSummary,
                language.choose("已覆盖待办项", "Covered Backlog Items"),
                ImplementationArtifactRenderSupport.renderBulletList(completedSubtasks)
        );
    }
}
