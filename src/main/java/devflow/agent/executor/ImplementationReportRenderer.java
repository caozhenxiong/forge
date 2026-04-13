package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import java.util.List;

/**
 * 负责 implementation 主报告与 repair alignment 的 markdown 渲染。
 * 这个类只面向单一状态源快照，不读取工作区，也不做阶段判断。
 */
final class ImplementationReportRenderer {

    private final ImplementationStageStatusArtifactRenderer stageStatusArtifactRenderer = new ImplementationStageStatusArtifactRenderer();
    private final ImplementationStageStatusSectionRenderer stageStatusSectionRenderer = new ImplementationStageStatusSectionRenderer();
    private final ImplementationSubtaskBreakdownRenderer subtaskBreakdownRenderer = new ImplementationSubtaskBreakdownRenderer();
    private final ImplementationSubtaskReportRenderer subtaskReportRenderer = new ImplementationSubtaskReportRenderer();
    private final ImplementationDiagnosticRenderer diagnosticRenderer = new ImplementationDiagnosticRenderer();

    String renderReport(ImplementationRuntimeSnapshot snapshot) {
        ImplementationPlan plan = snapshot.plan();
        List<SubtaskExecutionReport> reports = snapshot.reports();
        String note = snapshot.note();
        DocumentLanguage language = snapshot.language();
        ImplementationStageStatus stageStatus = snapshot.stageStatus();
        String currentSubtaskTitle = snapshot.currentSubtaskTitle();
        String stageStatusBlock = stageStatusArtifactRenderer.renderBlock(stageStatus);
        String diagnosticsSection = diagnosticRenderer.renderReportSection(snapshot);
        StringBuilder builder = new StringBuilder("""
                %s

                %s

                # %s

                ## %s

                %s

                ## %s

        """.formatted(
                stageStatusBlock,
                diagnosticsSection,
                language.choose("代码实现", "Implementation"),
                language.choose("实现摘要", "Implementation Summary"),
                plan.summary(),
                ""
        ));
        builder.append(stageStatusSectionRenderer.render(stageStatus, language));
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
