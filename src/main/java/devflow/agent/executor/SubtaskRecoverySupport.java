package devflow.agent.executor;

import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.review.FixMode;
import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.supervisor.DeliveryPolicyMode;
import devflow.agent.supervisor.GenerationRecoveryAction;
import devflow.agent.supervisor.GenerationRecoveryDecision;
import devflow.agent.supervisor.SupervisorAgent;
import java.nio.file.Path;
import java.util.List;

/**
 * 子任务生成失败后的恢复支撑。
 *
 * <p>它负责：
 * 1. 基于当前失败与 supervisor 决定下一轮恢复动作；
 * 2. 归一恢复策略，避免重新降级到 whole-file；
 * 3. 拼装 generation retry feedback；
 * 4. 合并 repair brief 与瞬时反馈。
 *
 * <p>这样 `SubtaskExecutor` 只保留尝试循环，不再混着恢复策略和 directive 拼装。
 */
final class SubtaskRecoverySupport {

    private final SupervisorAgent supervisorAgent;

    SubtaskRecoverySupport(SupervisorAgent supervisorAgent) {
        this.supervisorAgent = supervisorAgent;
    }

    GenerationRecoveryDecision decideGenerationRecovery(
            Path projectPath,
            devflow.agent.orchestrator.RunRecord runRecord,
            Subtask subtask,
            String feedback,
            int subtaskAttempt,
            GenerationFailureReport failureReport,
            SubtaskExecutionState executionState
    ) {
        DeliveryPolicy currentPolicy = new DeliveryPolicy(
                DeliveryPolicyMode.fromWireValue(executionState.deliveryMode().name(), DeliveryPolicyMode.PATCH),
                1,
                1,
                executionState.preferPreciseEditing(),
                false,
                true
        );
        if (supervisorAgent == null) {
            return normalizeRecoveryDecision(
                    new GenerationRecoveryDecision(
                            subtaskAttempt >= 2 ? GenerationRecoveryAction.ROUTE_TO_REPAIR : GenerationRecoveryAction.RETRY_SUBTASK,
                            new DeliveryPolicy(
                                    DeliveryPolicyMode.fromWireValue(executionState.deliveryMode().name(), DeliveryPolicyMode.PATCH),
                                    1,
                                    1,
                                    false,
                                    false,
                                    true
                            ),
                            subtaskAttempt >= 2
                                    ? "连续生成失败，切到更保守的 repair 式重试。"
                                    : "先按更保守的交付策略重试当前子任务。",
                            List.of(nullToEmpty(failureReport.summary()), nullToEmpty(failureReport.retryHint())).stream()
                                    .filter(item -> item != null && !item.isBlank())
                                    .toList(),
                            List.of("不要整文件重写", "优先收缩改单范围"),
                            List.of(failureReport.evidence()).stream()
                                    .filter(item -> item != null && !item.isBlank())
                                    .toList()
                    )
            );
        }
        return normalizeRecoveryDecision(
                supervisorAgent.decideGenerationFailure(
                        projectPath,
                        runRecord,
                        failureReport,
                        subtaskAttempt,
                        subtask.title(),
                        subtask.goal(),
                        feedback,
                        currentPolicy
                )
        );
    }

    String buildGenerationRetryFeedback(
            GenerationFailureReport failureReport,
            GenerationRecoveryDecision recoveryDecision
    ) {
        DeliveryPolicy policy = recoveryDecision.deliveryPolicy();
        String directiveBlock = ExecutionDirectiveProtocol.renderBlock(
                new ExecutionDirectivePayload(
                        FixMode.PATCH.name(),
                        false,
                        false,
                        policy.mode() == null ? null : policy.mode().wireValue(),
                        policy.maxFiles(),
                        policy.maxSymbols(),
                        policy.preferPreciseEditing(),
                        policy.forceBacklogSplit(),
                        policy.requireVerificationBeforeReview(),
                        recoveryDecision.requiredEvidence(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null,
                        null,
                        null,
                        recoveryDecision.action().name(),
                        recoveryDecision.reason(),
                        recoveryDecision.focus(),
                        recoveryDecision.constraints(),
                        failureReport.summary(),
                        failureReport.failureType().name(),
                        failureReport.evidence(),
                        failureReport.retryHint()
                )
        );
        return """
                %s

                generation failure:
                - summary: %s
                - failureType: %s
                - evidence: %s
                - retryHint: %s
                - supervisorAction: %s
                - supervisorReason: %s
                """.formatted(
                directiveBlock,
                nullToEmpty(failureReport.summary()),
                failureReport.failureType(),
                nullToEmpty(failureReport.evidence()),
                nullToEmpty(failureReport.retryHint()),
                recoveryDecision.action(),
                nullToEmpty(recoveryDecision.reason())
        ).strip();
    }

    String mergeFeedback(String persistentRepairFeedback, String transientFeedback) {
        String persistent = persistentRepairFeedback == null ? "" : persistentRepairFeedback.strip();
        String transientText = transientFeedback == null ? "" : transientFeedback.strip();
        if (persistent.isBlank()) {
            return transientText;
        }
        if (transientText.isBlank()) {
            return persistent;
        }
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(transientText);
        if (Boolean.TRUE.equals(directives.repairBriefPresent()) || Boolean.TRUE.equals(directives.repairBriefEnforced())) {
            return transientText;
        }
        return persistent + "\n\n" + transientText;
    }

    private GenerationRecoveryDecision normalizeRecoveryDecision(GenerationRecoveryDecision decision) {
        if (decision == null) {
            return null;
        }
        DeliveryPolicy policy = decision.deliveryPolicy();
        if (policy == null) {
            return decision;
        }
        // 局部编辑已经是硬主路径。
        // 这里不再根据失败类型把下一轮自动降级成 whole-file，只保留收缩范围的策略能力。
        return new GenerationRecoveryDecision(
                decision.action(),
                policy,
                decision.reason(),
                decision.focus(),
                decision.constraints(),
                decision.requiredEvidence()
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
