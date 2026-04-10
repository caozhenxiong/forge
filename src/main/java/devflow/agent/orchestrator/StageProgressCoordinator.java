package devflow.agent.orchestrator;

import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ContextProjector;
import devflow.agent.context.ProjectedContext;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.loop.LoopStepResult;
import devflow.agent.loop.TransitionDecision;
import devflow.agent.loop.TransitionReason;
import devflow.agent.review.ImplementationStageReadiness;
import devflow.agent.review.ImplementationStageReadinessParser;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorAgent;
import devflow.agent.supervisor.SupervisorDecision;
import java.nio.file.Path;
import java.util.Map;

/**
 * 负责执行单次 stage progress：
 * 1. 读取当前阶段产物
 * 2. 执行 stage review
 * 3. 投影上下文并请求 supervisor 决策
 * 4. 交给 FlowController 与 StageTransitionSupport 推进下一步
 *
 * 这样 workflow engine 可以只保留 run 生命周期入口，而不再手工串整段阶段编排。
 */
public class StageProgressCoordinator {

    private final FileArtifactStore artifactStore;
    private final DiagnosisAgent diagnosisAgent;
    private final SupervisorAgent supervisorAgent;
    private final FlowController flowController;
    private final ContextProjector contextProjector;
    private final StageOperationExecutor stageOperationExecutor;
    private final FlowDecisionExecutor flowDecisionExecutor;
    private final StageProgressArtifactSupport artifactSupport;
    private final StageToolResultLoader toolResultLoader;
    private final StageToolResultGuard toolResultGuard;
    private final ImplementationStageReadinessParser implementationStageReadinessParser;

    public StageProgressCoordinator(
            FileArtifactStore artifactStore,
            DiagnosisAgent diagnosisAgent,
            SupervisorAgent supervisorAgent,
            FlowController flowController,
            ContextProjector contextProjector,
            StageOperationExecutor stageOperationExecutor,
            FlowDecisionExecutor flowDecisionExecutor,
            StageProgressArtifactSupport artifactSupport,
            StageToolResultLoader toolResultLoader,
            StageToolResultGuard toolResultGuard
    ) {
        this.artifactStore = artifactStore;
        this.diagnosisAgent = diagnosisAgent;
        this.supervisorAgent = supervisorAgent;
        this.flowController = flowController;
        this.contextProjector = contextProjector;
        this.stageOperationExecutor = stageOperationExecutor;
        this.flowDecisionExecutor = flowDecisionExecutor;
        this.artifactSupport = artifactSupport;
        this.toolResultLoader = toolResultLoader;
        this.toolResultGuard = toolResultGuard;
        this.implementationStageReadinessParser = new ImplementationStageReadinessParser();
    }

    public LoopStepResult progress(Path projectPath, RunRecord current) {
        StageType stageType = current.currentStage();
        StageExecution stageExecution = requireStage(current.stageStates(), stageType);
        if (stageExecution.status() != StageStatus.RUNNING) {
            return new LoopStepResult(current, null, false);
        }

        DocumentLanguage language = DocumentLanguage.detect(current.goal(), current.constraints());
        String artifactContent = artifactStore.readArtifact(projectPath, current.runId(), stageType);
        if (stageType == StageType.IMPLEMENTATION) {
            ImplementationStageReadiness readiness = implementationStageReadinessParser.parse(artifactContent);
            if (!readiness.stageReady()) {
                return continueIncompleteImplementation(projectPath, current, stageType, readiness, language);
            }
        }
        ReviewResult reviewed = stageOperationExecutor.reviewStage(projectPath, current, stageType, stageExecution, artifactContent, language);
        StageToolResultSummary toolSummary = toolResultLoader.load(projectPath, current, stageType);
        ReviewResult reviewResult = toolResultGuard.guard(stageType, reviewed, toolSummary);
        artifactSupport.writeReviewArtifacts(projectPath, current, stageType, stageExecution, reviewResult, language);

        boolean repeatedIssue = reviewResult.decision() != ReviewDecision.APPROVED
                && stageType != StageType.ANALYSIS
                && diagnosisAgent.shouldDiagnose(
                projectPath,
                current,
                stageType,
                reviewResult.fixMode(),
                reviewResult.summary(),
                reviewResult.changeRequest()
        );

        ProjectedContext projectedContext = contextProjector.project(projectPath, current, stageType);
        artifactSupport.writeProjectedContextArtifacts(projectPath, current, projectedContext, language);

        SupervisorDecision supervisorDecision = supervisorAgent.decide(projectPath, current, stageType, reviewResult, repeatedIssue);
        artifactSupport.writeSupervisorArtifacts(
                projectPath,
                current,
                stageType,
                reviewResult,
                repeatedIssue,
                supervisorAgent,
                supervisorDecision,
                language
        );

        FlowDecision flowDecision = flowController.decide(stageType, reviewResult, repeatedIssue, supervisorDecision, toolSummary);
        TransitionDecision transitionDecision = flowDecision.transitionDecision();
        artifactSupport.writeTransitionArtifacts(projectPath, current, supervisorDecision, repeatedIssue, transitionDecision, language);

        RunRecord next = flowDecisionExecutor.apply(
                projectPath,
                current,
                stageType,
                reviewResult,
                repeatedIssue,
                supervisorDecision,
                flowDecision
        );
        return new LoopStepResult(next, transitionDecision, flowController.shouldContinue(next));
    }

    private LoopStepResult continueIncompleteImplementation(
            Path projectPath,
            RunRecord current,
            StageType stageType,
            ImplementationStageReadiness readiness,
            DocumentLanguage language
    ) {
        TransitionDecision transitionDecision = new TransitionDecision(
                TransitionReason.STAGE_CONTINUE,
                stageType,
                stageType,
                false,
                readiness.summary(),
                null
        );
        artifactSupport.writeContinuationArtifacts(
                projectPath,
                current,
                transitionDecision,
                language
        );
        RunRecord next = flowDecisionExecutor.continueStage(
                projectPath,
                current,
                stageType,
                readiness.summary(),
                readiness.changeRequest()
        );
        return new LoopStepResult(next, transitionDecision, flowController.shouldContinue(next));
    }

    private StageExecution requireStage(Map<StageType, StageExecution> stageStates, StageType stageType) {
        StageExecution stageExecution = stageStates.get(stageType);
        if (stageExecution == null) {
            throw new IllegalArgumentException("Missing stage state for " + stageType);
        }
        return stageExecution;
    }
}
