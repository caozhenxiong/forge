package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;

/**
 * 一次子任务尝试在 turn loop 里共享的不变上下文。
 *
 * <p>把这部分参数打包后，step 执行器不需要继续携带十几个离散参数，
 * 也能避免状态机实现再次长成大方法。
 */
record SubtaskAttemptContext(
        Path projectPath,
        RunRecord runRecord,
        String planSummary,
        Subtask subtask,
        TaskPackage taskPackage,
        String feedback,
        SubtaskExecutionState executionState,
        ContractView contractView,
        QualityPlan qualityPlan,
        ProjectFingerprint fingerprint,
        boolean finalSubtask,
        DocumentLanguage language,
        String coderContextMarkdown,
        ImplementationEventJournal eventJournal
) {
}
