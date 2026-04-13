package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.loop.AgentTurnSnapshot;
import devflow.agent.domain.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;

/**
 * 负责一次子任务尝试的完整 turn loop。
 * 它只覆盖 prepare/select/apply/observe/review 这一个闭环，不决定多轮重试与恢复策略。
 */
public final class SubtaskAttemptRunner {

    private final AgentTurnLoop agentTurnLoop;
    private final SubtaskAttemptStepExecutor stepExecutor;

    public SubtaskAttemptRunner(
            SubtaskAttemptStepExecutor stepExecutor,
            AgentTurnLoop agentTurnLoop
    ) {
        this.stepExecutor = stepExecutor;
        this.agentTurnLoop = agentTurnLoop;
    }

    SubtaskAttemptResult run(SubtaskAttemptContext context) {
        SubtaskAttemptProgress progress = new SubtaskAttemptProgress();
        agentTurnLoop.runUntilSettled(
                AgentTurnSnapshot.start(),
                snapshot -> stepExecutor.handle(snapshot, context, progress)
        );
        return progress.toResult();
    }
}
