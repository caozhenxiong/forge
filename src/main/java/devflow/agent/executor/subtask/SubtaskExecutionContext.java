package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.supervisor.DeliveryPolicy;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.DeliveryPolicyEnvelope;
/**
 * 一次子任务执行在多轮 attempt 之间共享的不变上下文。
 *
 * <p>execution 级上下文负责：
 * 1. 保存跨 attempt 复用的输入与约束；
 * 2. 区分 execution 级状态与单次 attempt 级状态；
 * 3. 为 {@link SubtaskAttemptRunner} 派生单次 attempt 所需的固定快照。
 */
public record SubtaskExecutionContext(
        Path projectPath,
        RunRecord runRecord,
        String planSummary,
        Subtask subtask,
        TaskPackage taskPackage,
        String inheritedFeedback,
        String persistentRepairFeedback,
        DeliveryPolicyEnvelope deliveryPolicy,
        ContractView contractView,
        QualityPlan qualityPlan,
        ProjectFingerprint fingerprint,
        boolean finalSubtask,
        DocumentLanguage language,
        String coderContextMarkdown,
        ImplementationEventJournal eventJournal,
        SubtaskExecutionState initialExecutionState
) {

    String inheritedFeedbackOrEmpty() {
        return inheritedFeedback == null ? "" : inheritedFeedback;
    }

    SubtaskExecutionState initialExecutionStateOrDefault() {
        return initialExecutionState == null
                ? new SubtaskExecutionState(
                        subtask.deliveryMode(),
                        deliveryPolicy.preferPreciseEditing()
                )
                : initialExecutionState.copy();
    }

    SubtaskAttemptContext toAttemptContext(String feedback, SubtaskExecutionState executionState) {
        return new SubtaskAttemptContext(
                projectPath,
                runRecord,
                planSummary,
                subtask,
                taskPackage,
                feedback,
                executionState,
                contractView,
                qualityPlan,
                fingerprint,
                finalSubtask,
                language,
                coderContextMarkdown,
                eventJournal
        );
    }
}
