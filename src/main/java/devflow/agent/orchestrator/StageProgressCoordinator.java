package devflow.agent.orchestrator;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.context.ContextProjector;
import devflow.agent.context.ProjectedContext;
import devflow.agent.executor.implementation.state.ImplementationStateArtifactSupport;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.loop.LoopStepResult;
import devflow.agent.loop.TransitionDecision;
import devflow.agent.loop.TransitionReason;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorAgent;
import devflow.agent.supervisor.SupervisorDecision;
import java.nio.file.Path;

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
    private final ImplementationStateArtifactSupport implementationStateSupport;
    private final ImplementationContinuationSupport implementationContinuationSupport;
    private final LanguagePolicy languagePolicy;

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
            StageToolResultGuard toolResultGuard,
            ImplementationStateArtifactSupport implementationStateSupport,
            ImplementationContinuationSupport implementationContinuationSupport,
            LanguagePolicy languagePolicy
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
        this.implementationStateSupport = implementationStateSupport;
        this.implementationContinuationSupport = implementationContinuationSupport;
        this.languagePolicy = languagePolicy;
    }

    public LoopStepResult progress(Path projectPath, RunRecord current) {
        StageType stageType = current.currentStage();
        StageExecution stageExecution = StageStatusSupport.requireStage(current.stageStates(), stageType);
        if (stageExecution.status() != StageStatus.RUNNING) {
            return new LoopStepResult(current, null, false);
        }

        DocumentLanguage language = languagePolicy.resolve(current.goal(), current.constraints());
        String artifactContent;
        if (stageType == StageType.IMPLEMENTATION) {
            String implementationStateJson = readImplementationStateArtifact(projectPath, current);
            ImplementationStageStatusPayload stageStatus = implementationStateSupport.readStageStatus(implementationStateJson);
            if (!stageStatus.stageReady()) {
                if (stageStatus.continuationMode() == ImplementationContinuationMode.BLOCK_STAGE) {
                    return blockImplementationForHuman(projectPath, current, stageType, stageStatus, language);
                }
                return continueIncompleteImplementation(projectPath, current, stageType, stageStatus, language);
            }
            artifactContent = implementationStateSupport.renderImplementationReviewSummary(implementationStateJson);
        } else {
            artifactContent = artifactStore.readArtifact(projectPath, current.runId(), stageType);
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
            ImplementationStageStatusPayload stageStatus,
            DocumentLanguage language
    ) {
        StageContinuationContext continuationContext = implementationContinuationSupport.toContinuationContext(stageStatus);
        TransitionDecision transitionDecision = new TransitionDecision(
                TransitionReason.STAGE_CONTINUE,
                stageType,
                stageType,
                false,
                continuationContext.summary(),
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
                continuationContext
        );
        return new LoopStepResult(next, transitionDecision, flowController.shouldContinue(next));
    }

    private LoopStepResult blockImplementationForHuman(
            Path projectPath,
            RunRecord current,
            StageType stageType,
            ImplementationStageStatusPayload stageStatus,
            DocumentLanguage language
    ) {
        StageContinuationContext continuationContext = implementationContinuationSupport.toContinuationContext(stageStatus);
        TransitionDecision transitionDecision = new TransitionDecision(
                TransitionReason.HUMAN_REVIEW_REQUIRED,
                stageType,
                stageType,
                false,
                continuationContext.summary(),
                null
        );
        artifactSupport.writeBlockedStageArtifacts(projectPath, current, transitionDecision, language);
        ReviewResult reviewResult = implementationContinuationSupport.toHumanReviewResult(continuationContext);
        RunRecord next = flowDecisionExecutor.blockForHumanReview(current, stageType, reviewResult);
        return new LoopStepResult(next, transitionDecision, flowController.shouldContinue(next));
    }

    private String readImplementationStateArtifact(Path projectPath, RunRecord current) {
        String content = artifactStore.readAuxiliaryArtifact(
                projectPath,
                current.runId(),
                AuxiliaryArtifactNames.IMPLEMENTATION_STATE
        );
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Missing implementation_state auxiliary artifact.");
        }
        return content;
    }

}
