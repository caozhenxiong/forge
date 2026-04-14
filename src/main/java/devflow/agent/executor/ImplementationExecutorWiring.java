package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.testing.TestExecutor;
import devflow.agent.executor.tools.ImplementationToolPermissionPolicy;
import devflow.agent.executor.tools.ImplementationToolPermissionProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContextLayerAssembler;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.QualityPlanFactory;
import devflow.agent.supervisor.SupervisorAgent;
import devflow.agent.validation.ProjectInspector;
import java.util.concurrent.ExecutorService;

import devflow.agent.executor.implementation.toolloop.ImplementationToolLoopExecutor;
import devflow.agent.executor.subtask.SubtaskAttemptRunner;
import devflow.agent.executor.subtask.SubtaskAttemptStepExecutor;
import devflow.agent.executor.subtask.SubtaskExecutor;
import devflow.agent.executor.subtask.SubtaskPerformanceGuidanceResolver;
import devflow.agent.executor.subtask.SubtaskRecoverySupport;
import devflow.agent.executor.subtask.SubtaskReviewPolicy;
import devflow.agent.executor.subtask.SubtaskVerificationSupport;
/**
 * implementation 子系统的唯一 wiring 入口。
 *
 * <p>它负责把 implementation 执行所需的协作者装配成稳定依赖图，
 * 避免再把对象创建链塞回 `ImplementationExecutor` 构造器。
 */
final class ImplementationExecutorWiring {

    private ImplementationExecutorWiring() {
    }

    static ImplementationExecutor create(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TestExecutor testExecutor,
            TreeSitterSupport treeSitterSupport,
            SupervisorAgent supervisorAgent,
            ContractExtractor contractExtractor,
            EventLogStore eventLogStore,
            FileArtifactStore fileArtifactStore,
            ExecutorService toolExecutor,
            LanguagePolicy languagePolicy,
            ImplementationExecutionPolicy implementationExecutionPolicy,
            RuntimeWorkingSetPolicy runtimeWorkingSetPolicy,
            SubtaskReviewPolicy subtaskReviewPolicy,
            ImplementationToolPermissionProperties implementationToolPermissionProperties,
            QualityPlanFactory qualityPlanFactory
    ) {
        ProjectInspector projectInspector = new ProjectInspector(workspace);
        ImplementationPlanCoverageAnalyzer coverageAnalyzer = new ImplementationPlanCoverageAnalyzer();
        ImplementationCompletenessCheck implementationCompletenessCheck =
                new ImplementationCompletenessCheck(workspace, treeSitterSupport);
        ArchitectIntegrationCheck architectIntegrationCheck =
                new ArchitectIntegrationCheck(workspace, treeSitterSupport);
        GenerationEngine generationEngine = new GenerationEngine();
        RuntimeWorkingSetResolver runtimeWorkingSetResolver = new RuntimeWorkingSetResolver(runtimeWorkingSetPolicy);
        TargetedFileContextRenderer targetedFileContextRenderer =
                new TargetedFileContextRenderer(workspace, runtimeWorkingSetResolver);
        ImplementationToolPermissionPolicy implementationToolPermissionPolicy =
                new ImplementationToolPermissionPolicy(implementationToolPermissionProperties, implementationExecutionPolicy);

        AgentTurnLoop planningTurnLoop = new AgentTurnLoop();
        AgentTurnLoop subtaskTurnLoop = new AgentTurnLoop();

        ImplementationPlanner implementationPlanner = new ImplementationPlanner(
                llmProvider,
                objectMapper,
                coverageAnalyzer,
                planningTurnLoop,
                implementationExecutionPolicy.planningPayloadRepairAttempts(),
                implementationExecutionPolicy.planningUnitAttempts(),
                implementationExecutionPolicy.maxFilesPerSubtask(),
                implementationExecutionPolicy.maxDeliveryPolicyFiles()
        );
        ImplementationStageGate implementationStageGate = new ImplementationStageGate();
        ImplementationGateEngine implementationGateEngine =
                new ImplementationGateEngine(implementationStageGate, architectIntegrationCheck);
        ImplementationResumePolicy implementationResumePolicy = new ImplementationResumePolicy(objectMapper);
        ImplementationArtifactRenderer implementationArtifactRenderer = new ImplementationArtifactRenderer(objectMapper);
        ImplementationToolLoopExecutor implementationToolLoopExecutor = new ImplementationToolLoopExecutor(
                llmProvider,
                objectMapper,
                implementationExecutionPolicy.toolLoopTurns(),
                implementationToolPermissionPolicy,
                toolExecutor
        );
        ImplementationSnapshotAssembler implementationSnapshotAssembler = new ImplementationSnapshotAssembler(
                implementationArtifactRenderer,
                targetedFileContextRenderer
        );
        ImplementationContextResolver implementationContextResolver = new ImplementationContextResolver(
                workspace,
                projectInspector,
                contractExtractor,
                new ContextLayerAssembler(),
                objectMapper,
                implementationExecutionPolicy.maxFilesPerSubtask(),
                implementationExecutionPolicy.maxDeliveryPolicyFiles(),
                languagePolicy,
                qualityPlanFactory
        );
        SubtaskPerformanceGuidanceResolver subtaskPerformanceGuidanceResolver =
                new SubtaskPerformanceGuidanceResolver(contractExtractor);
        ImplementationCompletenessGate implementationCompletenessGate =
                new ImplementationCompletenessGate(implementationCompletenessCheck);
        SubtaskVerificationSupport subtaskVerificationSupport = new SubtaskVerificationSupport(
                testExecutor,
                llmProvider,
                generationEngine,
                implementationCompletenessGate,
                architectIntegrationCheck,
                workspace,
                subtaskTurnLoop,
                subtaskReviewPolicy,
                subtaskPerformanceGuidanceResolver,
                treeSitterSupport
        );
        SubtaskAttemptStepExecutor subtaskAttemptStepExecutor = new SubtaskAttemptStepExecutor(
                testExecutor,
                implementationCompletenessGate,
                targetedFileContextRenderer,
                implementationToolLoopExecutor,
                subtaskVerificationSupport
        );
        SubtaskAttemptRunner subtaskAttemptRunner = new SubtaskAttemptRunner(
                subtaskAttemptStepExecutor,
                subtaskTurnLoop
        );
        SubtaskRecoverySupport subtaskRecoverySupport = new SubtaskRecoverySupport(supervisorAgent);
        SubtaskExecutor subtaskExecutor = new SubtaskExecutor(
                subtaskVerificationSupport,
                subtaskRecoverySupport,
                subtaskAttemptRunner,
                implementationExecutionPolicy.subtaskAttempts()
        );
        ImplementationPlanRunner implementationPlanRunner = new ImplementationPlanRunner(subtaskExecutor);
        CoderTurnCoordinator coderTurnCoordinator = new CoderTurnCoordinator(
                eventLogStore,
                fileArtifactStore,
                implementationStageGate,
                implementationGateEngine,
                implementationResumePolicy,
                implementationPlanner,
                implementationPlanRunner,
                implementationSnapshotAssembler,
                implementationContextResolver
        );
        return new ImplementationExecutor(coderTurnCoordinator, toolExecutor);
    }
}
