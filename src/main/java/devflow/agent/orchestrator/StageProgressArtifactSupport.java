package devflow.agent.orchestrator;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ContextAccessProfile;
import devflow.agent.context.ProjectedContext;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.loop.TransitionDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorAgent;
import devflow.agent.supervisor.SupervisorDecision;
import java.nio.file.Path;

/**
 * 统一维护 stage progress 过程中产生的辅助产物与事件。
 *
 * <p>这样 StageProgressCoordinator 就能专注于：
 * 1. 何时 review
 * 2. 何时投影上下文
 * 3. 何时请求 supervisor 与 FlowController
 *
 * <p>而不是继续自己拼 review history、辅助文档和事件日志。
 */
public class StageProgressArtifactSupport {

    private final FileArtifactStore artifactStore;
    private final EventLogStore eventLogStore;
    private final WorkflowArtifactRenderer workflowArtifactRenderer;

    public StageProgressArtifactSupport(
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            WorkflowArtifactRenderer workflowArtifactRenderer
    ) {
        this.artifactStore = artifactStore;
        this.eventLogStore = eventLogStore;
        this.workflowArtifactRenderer = workflowArtifactRenderer;
    }

    public void writeReviewArtifacts(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            StageExecution stageExecution,
            ReviewResult reviewResult,
            DocumentLanguage language
    ) {
        artifactStore.writeReviewArtifact(
                projectPath,
                runRecord.runId(),
                stageType,
                workflowArtifactRenderer.renderReviewArtifact(stageType, reviewResult, language)
        );
        artifactStore.appendReviewHistory(
                projectPath,
                runRecord.runId(),
                stageType,
                workflowArtifactRenderer.renderReviewHistoryEntry(stageType, stageExecution.attempt(), "agent", reviewResult, language)
        );
        eventLogStore.append(
                projectPath,
                runRecord.runId(),
                WorkflowEventMessages.agentReviewed(stageType, reviewResult)
        );
    }

    public void writeProjectedContextArtifacts(
            Path projectPath,
            RunRecord runRecord,
            ProjectedContext projectedContext,
            DocumentLanguage language
    ) {
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.PROJECTED_CONTEXT, projectedContext.toMarkdown(language));
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.PLANNER_CONTEXT, projectedContext.toMarkdown(ContextAccessProfile.PLANNER, language));
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.CODER_CONTEXT, projectedContext.toMarkdown(ContextAccessProfile.CODER, language));
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.REVIEWER_CONTEXT, projectedContext.toMarkdown(ContextAccessProfile.REVIEWER, language));
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.FLOW_CONTROLLER_CONTEXT, projectedContext.toMarkdown(ContextAccessProfile.FLOW_CONTROLLER, language));
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.SUPERVISOR_CONTEXT, projectedContext.toMarkdown(ContextAccessProfile.SUPERVISOR, language));
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.TASK_MEMORY, projectedContext.taskMemory().toMarkdown(language));
    }

    public void writeSupervisorArtifacts(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            SupervisorAgent supervisorAgent,
            SupervisorDecision supervisorDecision,
            DocumentLanguage language
    ) {
        artifactStore.writeAuxiliaryArtifact(
                projectPath,
                runRecord.runId(),
                AuxiliaryArtifactNames.SUPERVISOR_DECISION,
                supervisorAgent.renderDecisionArtifact(stageType, reviewResult, repeatedIssue, supervisorDecision, language)
        );
    }

    public void writeTransitionArtifacts(
            Path projectPath,
            RunRecord runRecord,
            SupervisorDecision supervisorDecision,
            boolean repeatedIssue,
            TransitionDecision transitionDecision,
            DocumentLanguage language
    ) {
        artifactStore.writeAuxiliaryArtifact(
                projectPath,
                runRecord.runId(),
                AuxiliaryArtifactNames.TRANSITION_DECISION,
                workflowArtifactRenderer.renderTransitionDecision(transitionDecision, language)
        );
        eventLogStore.append(
                projectPath,
                runRecord.runId(),
                WorkflowEventMessages.supervisorDecided(supervisorDecision, repeatedIssue, transitionDecision)
        );
    }

    public void writeContinuationArtifacts(
            Path projectPath,
            RunRecord runRecord,
            TransitionDecision transitionDecision,
            DocumentLanguage language
    ) {
        artifactStore.writeAuxiliaryArtifact(
                projectPath,
                runRecord.runId(),
                AuxiliaryArtifactNames.TRANSITION_DECISION,
                workflowArtifactRenderer.renderTransitionDecision(transitionDecision, language)
        );
        eventLogStore.append(
                projectPath,
                runRecord.runId(),
                WorkflowEventMessages.stageContinued(transitionDecision)
        );
    }
}
