package devflow.agent.orchestrator;

import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.loop.TransitionDecision;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.FileChangePayload;
import devflow.agent.protocol.ReviewArtifactPayload;
import devflow.agent.protocol.ReviewHistoryEntryPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorDecision;

/**
 * 统一渲染 workflow 层的 review/transition 文本产物。
 * 这类字符串拼装不应该继续混在 DefaultWorkflowEngine 里，否则流程编排和展现格式会长期耦合。
 */
public class WorkflowArtifactRenderer {

    public String renderReviewArtifact(StageType stageType, ReviewResult reviewResult, DocumentLanguage language) {
        String machineBlock = StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.REVIEW_RESULT,
                new ReviewArtifactPayload(
                        reviewResult.decision().name(),
                        reviewResult.fixMode().name(),
                        reviewResult.implementationPatchTarget().name(),
                        reviewResult.overrideChanges().stream().map(this::toPayload).toList(),
                        reviewResult.revisionRoute().name(),
                        reviewResult.reasonCode().name(),
                        nullToEmpty(reviewResult.summary()),
                        nullToEmpty(reviewResult.changeRequest()),
                        nullToEmpty(reviewResult.evidence()),
                        nullToEmpty(reviewResult.actionItems()),
                        reviewResult.decision() != devflow.agent.review.ReviewDecision.APPROVED,
                        null,
                        null
                )
        );
        return """
                %s

                # %s

                决策：%s
                修复模式：%s
                修复目标：%s

                ## %s

                %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                %s
        """.formatted(
                machineBlock,
                language.choose(stageType + " 阶段审阅结果", stageType + " Review Result"),
                reviewResult.decision(),
                reviewResult.fixMode(),
                reviewResult.implementationPatchTarget(),
                language.choose("总结", "Summary"),
                emptySection(reviewResult.summary(), language),
                language.choose("修改要求", "Change Request"),
                emptySection(reviewResult.changeRequest(), language),
                language.choose("证据", "Evidence"),
                emptySection(reviewResult.evidence(), language),
                language.choose("执行动作", "Action Items"),
                emptySection(reviewResult.actionItems(), language)
        );
    }

    public String renderReviewHistoryEntry(StageType stageType, int attempt, String reviewer, ReviewResult reviewResult, DocumentLanguage language) {
        String machineBlock = StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.REVIEW_HISTORY_ENTRY,
                new ReviewHistoryEntryPayload(
                        attempt,
                        reviewer,
                        stageType.name(),
                        reviewResult.decision().name(),
                        reviewResult.fixMode().name(),
                        reviewResult.implementationPatchTarget().name(),
                        nullToEmpty(reviewResult.summary()),
                        nullToEmpty(reviewResult.changeRequest()),
                        nullToEmpty(reviewResult.evidence()),
                        nullToEmpty(reviewResult.actionItems())
                )
        );
        return """
                %s

                ## %s=%d %s=%s %s=%s

                决策：%s
                修复模式：%s
                修复目标：%s

                总结：%s
                修改要求：%s
                证据：%s
                执行动作：%s

                """.formatted(
                machineBlock,
                language.choose("轮次", "attempt"),
                attempt,
                language.choose("审阅者", "reviewer"),
                reviewer,
                language.choose("阶段", "stage"),
                stageType,
                reviewResult.decision(),
                reviewResult.fixMode(),
                reviewResult.implementationPatchTarget(),
                inlineEmpty(reviewResult.summary()),
                inlineEmpty(reviewResult.changeRequest()),
                inlineEmpty(reviewResult.evidence()),
                inlineEmpty(reviewResult.actionItems())
        );
    }

    public String renderTransitionDecision(TransitionDecision decision, DocumentLanguage language) {
        SupervisorDecision supervisorDecision = decision.supervisorDecision();
        return """
                # %s

                - reason: %s
                - fromStage: %s
                - targetStage: %s
                - repeatedIssue: %s
                - reviewSummary: %s
                - supervisorAction: %s
                - supervisorMode: %s

                ## %s

                - mode: %s
                - maxFiles: %s
                - maxSymbols: %s
                - preferPreciseEditing: %s
                - forceBacklogSplit: %s
                - requireVerificationBeforeReview: %s

                ## %s

                %s

                ## %s

                %s

                ## %s

                %s
                """.formatted(
                language.choose("流转决策", "Transition Decision"),
                decision.reason(),
                decision.fromStage(),
                PlaceholderValues.orMachineNull(decision.targetStage() == null ? null : decision.targetStage().toString()),
                decision.repeatedIssue(),
                nullToEmpty(decision.summary()),
                supervisorDecision == null ? PlaceholderValues.orMachineNull(null) : supervisorDecision.action(),
                supervisorDecision == null ? PlaceholderValues.orMachineNull(null) : supervisorDecision.mode(),
                language.choose("交付策略", "Delivery Policy"),
                supervisorDecision == null ? PlaceholderValues.orMachineNull(null) : supervisorDecision.deliveryPolicy().mode(),
                supervisorDecision == null ? PlaceholderValues.orMachineNull(null) : supervisorDecision.deliveryPolicy().maxFiles(),
                supervisorDecision == null ? PlaceholderValues.orMachineNull(null) : supervisorDecision.deliveryPolicy().maxSymbols(),
                supervisorDecision == null ? PlaceholderValues.orMachineNull(null) : supervisorDecision.deliveryPolicy().preferPreciseEditing(),
                supervisorDecision == null ? PlaceholderValues.orMachineNull(null) : supervisorDecision.deliveryPolicy().forceBacklogSplit(),
                supervisorDecision == null ? PlaceholderValues.orMachineNull(null) : supervisorDecision.deliveryPolicy().requireVerificationBeforeReview(),
                language.choose("关注点", "Focus"),
                supervisorDecision == null ? "" : renderList(supervisorDecision.focus()),
                language.choose("约束", "Constraints"),
                supervisorDecision == null ? "" : renderList(supervisorDecision.constraints()),
                ArtifactLabels.requiredEvidence(language),
                supervisorDecision == null ? "" : renderList(supervisorDecision.requiredEvidence())
        );
    }

    private String renderList(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ").append(value.trim());
        }
        return builder.toString();
    }

    private String emptySection(String value, DocumentLanguage language) {
        return value == null || value.isBlank()
                ? language.choose("无", "None")
                : value;
    }

    private String inlineEmpty(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private FileChangePayload toPayload(devflow.agent.executor.FileChange change) {
        if (change == null) {
            return null;
        }
        return new FileChangePayload(
                change.path(),
                change.action() == null ? null : change.action().name(),
                nullToEmpty(change.reason()),
                change.effectiveEditScope().name(),
                change.runtimeOwnership() == null ? null : change.runtimeOwnership().name(),
                change.hostHtmlPatchRequired()
        );
    }
}
