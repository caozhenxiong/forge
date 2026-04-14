package devflow.agent.orchestrator;

import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ContextProjector;
import devflow.agent.context.ProjectedContext;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.loop.LoopStepResult;
import devflow.agent.loop.TransitionDecision;
import devflow.agent.loop.TransitionReason;
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
 * <p>这样 workflow engine 可以只保留 run 生命周期入口，而不再手工串整段阶段编排。
 */
public class StageProgressCoordinator {

    private final FileArtifactStore artifactStore;
    private final SupervisorAgent supervisorAgent;
    private final FlowController flowController;
    private final ContextProjector contextProjector;
    private final StageOperationExecutor stageOperationExecutor;
    private final FlowDecisionExecutor flowDecisionExecutor;
    private final StageProgressArtifactSupport artifactSupport;
    private final StageToolResultGate stageToolResultGate;
    private final ImplementationProgressSupport implementationProgressSupport;
    private final RepeatIssueDetector repeatIssueDetector;
    private final LanguagePolicy languagePolicy;

    public StageProgressCoordinator(
            FileArtifactStore artifactStore,
            SupervisorAgent supervisorAgent,
            FlowController flowController,
            ContextProjector contextProjector,
            StageOperationExecutor stageOperationExecutor,
            FlowDecisionExecutor flowDecisionExecutor,
            StageProgressArtifactSupport artifactSupport,
            StageToolResultGate stageToolResultGate,
            ImplementationProgressSupport implementationProgressSupport,
            RepeatIssueDetector repeatIssueDetector,
            LanguagePolicy languagePolicy
    ) {
        this.artifactStore = artifactStore;
        this.supervisorAgent = supervisorAgent;
        this.flowController = flowController;
        this.contextProjector = contextProjector;
        this.stageOperationExecutor = stageOperationExecutor;
        this.flowDecisionExecutor = flowDecisionExecutor;
        this.artifactSupport = artifactSupport;
        this.stageToolResultGate = stageToolResultGate;
        this.implementationProgressSupport = implementationProgressSupport;
        this.repeatIssueDetector = repeatIssueDetector;
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
            ImplementationProgressState implementationProgress = implementationProgressSupport.read(projectPath, current);
            if (!implementationProgress.stageReady()) {
                if (implementationProgress.blocked()) {
                    return blockImplementationForHuman(projectPath, current, stageType, implementationProgress, language);
                }
                return continueIncompleteImplementation(projectPath, current, stageType, implementationProgress, language);
            }
            artifactContent = implementationProgress.reviewSummary();
        } else {
            artifactContent = artifactStore.readArtifact(projectPath, current.runId(), stageType);
        }
        ReviewResult reviewed = stageOperationExecutor.reviewStage(projectPath, current, stageType, stageExecution, artifactContent, language);
        StageToolResultGateResult gateResult = stageToolResultGate.apply(projectPath, current, stageType, reviewed);
        StageToolResultSummary toolSummary = gateResult.toolSummary();
        ReviewResult reviewResult = gateResult.reviewResult();
        artifactSupport.writeReviewArtifacts(projectPath, current, stageType, stageExecution, reviewResult, language);

        boolean repeatedIssue = repeatIssueDetector.shouldDiagnose(projectPath, current, stageType, reviewResult);

        ProjectedContext projectedContext = contextProjector.project(projectPath, current, stageType);
        artifactSupport.writeProjectedContextArtifacts(projectPath, current, projectedContext, language);

        SupervisorDecision supervisorDecision =
                supervisorAgent.decide(current, stageType, reviewResult, repeatedIssue, projectedContext);
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

        ImplementationRevisionFacts implementationRevisionFacts =
                resolveImplementationRevisionFacts(projectPath, current, supervisorDecision);
        FlowDecision flowDecision = flowController.decide(
                stageType,
                reviewResult,
                repeatedIssue,
                supervisorDecision,
                toolSummary,
                implementationRevisionFacts
        );
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

    private ImplementationRevisionFacts resolveImplementationRevisionFacts(
            Path projectPath,
            RunRecord current,
            SupervisorDecision supervisorDecision
    ) {
        if (supervisorDecision == null || supervisorDecision.targetStage() != StageType.IMPLEMENTATION) {
            return ImplementationRevisionFacts.none();
        }
        return implementationProgressSupport.readRevisionFacts(projectPath, current);
    }

    private LoopStepResult continueIncompleteImplementation(
            Path projectPath,
            RunRecord current,
            StageType stageType,
            ImplementationProgressState implementationProgress,
            DocumentLanguage language
    ) {
        StageContinuationContext continuationContext = implementationProgress.continuationContext();
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
            ImplementationProgressState implementationProgress,
            DocumentLanguage language
    ) {
        StageContinuationContext continuationContext = implementationProgress.continuationContext();
        TransitionDecision transitionDecision = new TransitionDecision(
                TransitionReason.HUMAN_REVIEW_REQUIRED,
                stageType,
                stageType,
                false,
                continuationContext.summary(),
                null
        );
        artifactSupport.writeBlockedStageArtifacts(projectPath, current, transitionDecision, language);
        ReviewResult reviewResult = implementationProgress.humanReviewResult();
        RunRecord next = flowDecisionExecutor.blockForHumanReview(current, stageType, reviewResult);
        return new LoopStepResult(next, transitionDecision, flowController.shouldContinue(next));
    }
}
