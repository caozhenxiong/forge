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
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            EventLogStore eventLogStore,
            StageFlowPolicy stageFlowPolicy,
            WorkflowArtifactRenderer workflowArtifactRenderer,
            SupervisorGuidanceRenderer supervisorGuidanceRenderer,
            StageRevisionRepairSupport stageRevisionRepairSupport,
            LanguagePolicy languagePolicy
    ) {
        return new StageRevisionSupport(
                runRepository,
                artifactStore,
                eventLogStore,
                stageFlowPolicy,
                workflowArtifactRenderer,
                supervisorGuidanceRenderer,
                stageRevisionRepairSupport,
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
            FileRunRepository runRepository,
            EventLogStore eventLogStore,
            StageStatusSupport stageStatusSupport,
            StageRevisionSupport stageRevisionSupport,
            StageContinuationNoteBuilder stageContinuationNoteBuilder
    ) {
        return new StageTransitionSupport(
                runRepository,
                eventLogStore,
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
    ImplementationStateArtifactSupport implementationStateArtifactSupport() {
        return new ImplementationStateArtifactSupport();
    }

    @Bean
    ImplementationContinuationSupport implementationContinuationSupport() {
        return new ImplementationContinuationSupport();
    }

    @Bean
    StageProgressCoordinator stageProgressCoordinator(
            FileArtifactStore artifactStore,
            DiagnosisAgent diagnosisAgent,
            SupervisorAgent supervisorAgent,
            FlowController flowController,
            ContextProjector contextProjector,
            StageOperationExecutor stageOperationExecutor,
            FlowDecisionExecutor flowDecisionExecutor,
            StageProgressArtifactSupport stageProgressArtifactSupport,
            StageToolResultLoader stageToolResultLoader,
            StageToolResultGuard stageToolResultGuard,
            ImplementationStateArtifactSupport implementationStateArtifactSupport,
            ImplementationContinuationSupport implementationContinuationSupport,
            LanguagePolicy languagePolicy
    ) {
        return new StageProgressCoordinator(
                artifactStore,
                diagnosisAgent,
                supervisorAgent,
                flowController,
                contextProjector,
                stageOperationExecutor,
                flowDecisionExecutor,
                stageProgressArtifactSupport,
                stageToolResultLoader,
                stageToolResultGuard,
                implementationStateArtifactSupport,
                implementationContinuationSupport,
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
