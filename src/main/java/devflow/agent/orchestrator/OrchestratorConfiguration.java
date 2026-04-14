package devflow.agent.orchestrator;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.artifact.StageArtifactComposer;
import devflow.agent.context.ContextProjector;
import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.implementation.state.ImplementationStateArtifactSupport;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.loop.AgentLoop;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.review.StageReviewer;
import devflow.agent.supervisor.SupervisorAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OrchestratorConfiguration {

    @Bean
    WorkflowArtifactRenderer workflowArtifactRenderer() {
        return new WorkflowArtifactRenderer();
    }

    @Bean
    StageOperationPolicy stageOperationPolicy() {
        return new StageOperationPolicy();
    }

    @Bean
    GenerationEngine stageReviewExecutionEngine() {
        return new GenerationEngine();
    }

    @Bean
    StageStatusSupport stageStatusSupport(
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            StageFlowPolicy stageFlowPolicy,
            WorkflowArtifactRenderer workflowArtifactRenderer,
            LanguagePolicy languagePolicy
    ) {
        return new StageStatusSupport(
                runRepository,
                artifactStore,
                eventLogStore,
                stageFlowPolicy,
                workflowArtifactRenderer,
                languagePolicy
        );
    }

    @Bean
    StageRevisionSupport stageRevisionSupport(
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            StageFlowPolicy stageFlowPolicy,
            WorkflowArtifactRenderer workflowArtifactRenderer,
            SupervisorGuidanceRenderer supervisorGuidanceRenderer,
            StageRevisionRepairSupport stageRevisionRepairSupport,
            StageStatusSupport stageStatusSupport,
            LanguagePolicy languagePolicy
    ) {
        return new StageRevisionSupport(
                artifactStore,
                eventLogStore,
                stageFlowPolicy,
                workflowArtifactRenderer,
                supervisorGuidanceRenderer,
                stageRevisionRepairSupport,
                stageStatusSupport,
                languagePolicy
        );
    }

    @Bean
    SupervisorGuidanceRenderer supervisorGuidanceRenderer() {
        return new SupervisorGuidanceRenderer();
    }

    @Bean
    StageRevisionNoteBuilder stageRevisionNoteBuilder() {
        return new StageRevisionNoteBuilder();
    }

    @Bean
    StageRevisionRepairSupport stageRevisionRepairSupport(
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            DiagnosisAgent diagnosisAgent,
            RepairAgent repairAgent,
            StageRevisionNoteBuilder stageRevisionNoteBuilder,
            LanguagePolicy languagePolicy
    ) {
        return new StageRevisionRepairSupport(
                artifactStore,
                eventLogStore,
                diagnosisAgent,
                repairAgent,
                stageRevisionNoteBuilder,
                languagePolicy
        );
    }

    @Bean
    StageContinuationNoteBuilder stageContinuationNoteBuilder() {
        return new StageContinuationNoteBuilder();
    }

    @Bean
    StageTransitionSupport stageTransitionSupport(
            StageStatusSupport stageStatusSupport,
            StageRevisionSupport stageRevisionSupport,
            StageContinuationNoteBuilder stageContinuationNoteBuilder
    ) {
        return new StageTransitionSupport(
                stageStatusSupport,
                stageRevisionSupport,
                stageContinuationNoteBuilder
        );
    }

    @Bean
    StageOperationExecutor stageOperationExecutor(
            StageArtifactComposer stageArtifactComposer,
            StageReviewer stageReviewer,
            EventLogStore eventLogStore,
            GenerationEngine stageReviewExecutionEngine,
            StageOperationPolicy stageOperationPolicy
    ) {
        return new StageOperationExecutor(
                stageArtifactComposer,
                stageReviewer,
                eventLogStore,
                stageReviewExecutionEngine,
                stageOperationPolicy
        );
    }

    @Bean
    StageEntryExecutor stageEntryExecutor(
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            StageOperationExecutor stageOperationExecutor,
            StageTransitionSupport stageTransitionSupport
    ) {
        return new StageEntryExecutor(
                runRepository,
                artifactStore,
                eventLogStore,
                stageOperationExecutor,
                stageTransitionSupport
        );
    }

    @Bean
    FlowDecisionExecutor flowDecisionExecutor(
            StageTransitionSupport stageTransitionSupport,
            StageEntryExecutor stageEntryExecutor
    ) {
        return new FlowDecisionExecutor(stageTransitionSupport, stageEntryExecutor);
    }

    @Bean
    StageProgressArtifactSupport stageProgressArtifactSupport(
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            WorkflowArtifactRenderer workflowArtifactRenderer
    ) {
        return new StageProgressArtifactSupport(artifactStore, eventLogStore, workflowArtifactRenderer);
    }

    @Bean
    StageToolResultLoader stageToolResultLoader(FileArtifactStore artifactStore) {
        return new StageToolResultLoader(artifactStore);
    }

    @Bean
    StageToolResultGuard stageToolResultGuard() {
        return new StageToolResultGuard();
    }

    @Bean
    StageToolResultGate stageToolResultGate(
            StageToolResultLoader stageToolResultLoader,
            StageToolResultGuard stageToolResultGuard
    ) {
        return new StageToolResultGate(stageToolResultLoader, stageToolResultGuard);
    }

    @Bean
    ImplementationStateArtifactSupport implementationStateArtifactSupport() {
        return new ImplementationStateArtifactSupport();
    }

    @Bean
    ImplementationContinuationSupport implementationContinuationSupport() {
        return new ImplementationContinuationSupport();
    }

    @Bean
    ImplementationProgressSupport implementationProgressSupport(
            FileArtifactStore artifactStore,
            ImplementationStateArtifactSupport implementationStateArtifactSupport,
            ImplementationContinuationSupport implementationContinuationSupport
    ) {
        return new ImplementationProgressSupport(
                artifactStore,
                implementationStateArtifactSupport,
                implementationContinuationSupport
        );
    }

    @Bean
    RepeatIssueDetector repeatIssueDetector(DiagnosisAgent diagnosisAgent) {
        return new RepeatIssueDetector(diagnosisAgent);
    }

    @Bean
    StageProgressCoordinator stageProgressCoordinator(
            FileArtifactStore artifactStore,
            SupervisorAgent supervisorAgent,
            FlowController flowController,
            ContextProjector contextProjector,
            StageOperationExecutor stageOperationExecutor,
            FlowDecisionExecutor flowDecisionExecutor,
            StageProgressArtifactSupport stageProgressArtifactSupport,
            StageToolResultGate stageToolResultGate,
            ImplementationProgressSupport implementationProgressSupport,
            RepeatIssueDetector repeatIssueDetector,
            LanguagePolicy languagePolicy
    ) {
        return new StageProgressCoordinator(
                artifactStore,
                supervisorAgent,
                flowController,
                contextProjector,
                stageOperationExecutor,
                flowDecisionExecutor,
                stageProgressArtifactSupport,
                stageToolResultGate,
                implementationProgressSupport,
                repeatIssueDetector,
                languagePolicy
        );
    }

    @Bean
    WorkflowRunLifecycleSupport workflowRunLifecycleSupport(
            FileRunRepository runRepository,
            EventLogStore eventLogStore,
            WorkspaceSnapshotStore snapshotStore,
            StageTransitionSupport stageTransitionSupport,
            StageEntryExecutor stageEntryExecutor,
            StageProgressCoordinator stageProgressCoordinator,
            AgentLoop agentLoop
    ) {
        return new WorkflowRunLifecycleSupport(
                runRepository,
                eventLogStore,
                snapshotStore,
                stageTransitionSupport,
                stageEntryExecutor,
                stageProgressCoordinator,
                agentLoop
        );
    }
}
