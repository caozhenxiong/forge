package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.context.ContextLayerAssembler;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectInspector;
import java.nio.file.Path;
import devflow.agent.supervisor.SupervisorAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
/**
 * Implementation 阶段的门面编排器。
 *
 * <p>这个类只负责把已经拆开的 implementation 组件串起来：
 * 解析执行上下文、决定是否复用上一轮状态、发布中间快照、调用 plan runner，
 * 最后再把 gate 结果装配成对外 bundle。
 *
 * <p>它不再直接持有 planning retry、阶段 gate、子任务顺序执行、状态复用等流程细节，
 * 这些职责已经分别下沉到独立组件中。
 */
public class ImplementationExecutor {
    private final CoderTurnCoordinator coderTurnCoordinator;

    public ImplementationExecutor(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TestExecutor testExecutor
    ) {
        this(llmProvider, workspace, objectMapper, testExecutor, new TreeSitterSupport(), null, new ContractExtractor(), null, null);
    }

    public ImplementationExecutor(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TestExecutor testExecutor,
            TreeSitterSupport treeSitterSupport,
            @Autowired(required = false) SupervisorAgent supervisorAgent,
            @Autowired(required = false) ContractExtractor contractExtractor
    ) {
        this(llmProvider, workspace, objectMapper, testExecutor, treeSitterSupport, supervisorAgent, contractExtractor, null, null);
    }

    @Autowired
    public ImplementationExecutor(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TestExecutor testExecutor,
            TreeSitterSupport treeSitterSupport,
            @Autowired(required = false) SupervisorAgent supervisorAgent,
            @Autowired(required = false) ContractExtractor contractExtractor,
            @Autowired(required = false) EventLogStore eventLogStore,
            @Autowired(required = false) FileArtifactStore fileArtifactStore
    ) {
        ContractExtractor effectiveContractExtractor = contractExtractor == null ? new ContractExtractor() : contractExtractor;
        ProjectInspector projectInspector = new ProjectInspector(workspace);
        ImplementationPlanCoverageAnalyzer implementationPlanCoverageAnalyzer = new ImplementationPlanCoverageAnalyzer();
        ImplementationCompletenessCheck implementationCompletenessCheck = new ImplementationCompletenessCheck(workspace, treeSitterSupport);
        ArchitectIntegrationCheck architectIntegrationCheck = new ArchitectIntegrationCheck(workspace, treeSitterSupport);
        var htmlPreciseEditor = new devflow.agent.editing.HtmlPreciseEditor(treeSitterSupport);
        var codePreciseEditor = new devflow.agent.editing.CodePreciseEditor(treeSitterSupport);
        var htmlDocumentAssembler = new devflow.agent.editing.HtmlDocumentAssembler();
        var generationEngine = new GenerationEngine();
        var runtimeWorkingSetResolver = new RuntimeWorkingSetResolver();
        ImplementationPlanner implementationPlanner = new ImplementationPlanner(
                llmProvider,
                objectMapper,
                implementationPlanCoverageAnalyzer,
                new AgentTurnLoop(),
                ImplementationExecutionPolicy.planningPayloadRepairAttempts(),
                ImplementationExecutionPolicy.planningUnitAttempts(),
                ImplementationExecutionPolicy.maxFilesPerSubtask(),
                ImplementationExecutionPolicy.maxDeliveryPolicyFiles()
        );
        ImplementationStageGate implementationStageGate = new ImplementationStageGate();
        ImplementationGateEngine implementationGateEngine = new ImplementationGateEngine(implementationStageGate, architectIntegrationCheck);
        ImplementationResumePolicy implementationResumePolicy = new ImplementationResumePolicy(objectMapper);
        ImplementationArtifactRenderer implementationArtifactRenderer = new ImplementationArtifactRenderer(objectMapper);
        FileEditCoordinator fileEditCoordinator = new FileEditCoordinator(
                llmProvider,
                workspace,
                objectMapper,
                treeSitterSupport,
                htmlPreciseEditor,
                codePreciseEditor,
                htmlDocumentAssembler,
                generationEngine,
                runtimeWorkingSetResolver,
                ImplementationExecutionPolicy.fileGenerationAttempts()
        );
        ImplementationSnapshotAssembler implementationSnapshotAssembler = new ImplementationSnapshotAssembler(
                implementationArtifactRenderer,
                fileEditCoordinator
        );
        ImplementationContextResolver implementationContextResolver = new ImplementationContextResolver(
                workspace,
                projectInspector,
                effectiveContractExtractor,
                new ContextLayerAssembler(),
                objectMapper,
                ImplementationExecutionPolicy.maxFilesPerSubtask(),
                ImplementationExecutionPolicy.maxDeliveryPolicyFiles()
        );
        SubtaskExecutor subtaskExecutor = new SubtaskExecutor(
                llmProvider,
                testExecutor,
                implementationCompletenessCheck,
                architectIntegrationCheck,
                supervisorAgent,
                workspace,
                fileEditCoordinator,
                generationEngine,
                new AgentTurnLoop(),
                ImplementationExecutionPolicy.subtaskAttempts()
        );
        ImplementationPlanRunner implementationPlanRunner = new ImplementationPlanRunner(subtaskExecutor);
        this.coderTurnCoordinator = new CoderTurnCoordinator(
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
    }

    public ImplementationExecutionBundle execute(Path projectPath, RunRecord runRecord, String analysis, String prd, String design, String note) {
        return execute(projectPath, runRecord, analysis, prd, design, note, null, "", ImplementationProgressSink.noop());
    }

    public ImplementationExecutionBundle execute(
            Path projectPath,
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            ContractView authoritativeContractView
    ) {
        return execute(projectPath, runRecord, analysis, prd, design, note, authoritativeContractView, "", ImplementationProgressSink.noop());
    }

    public ImplementationExecutionBundle execute(
            Path projectPath,
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            ContractView authoritativeContractView,
            String previousStateJson
    ) {
        return execute(
                projectPath,
                runRecord,
                analysis,
                prd,
                design,
                note,
                authoritativeContractView,
                previousStateJson,
                ImplementationProgressSink.noop()
        );
    }

    public ImplementationExecutionBundle execute(
            Path projectPath,
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            ContractView authoritativeContractView,
            String previousStateJson,
            ImplementationProgressSink progressSink
    ) {
        return coderTurnCoordinator.execute(
                projectPath,
                runRecord,
                analysis,
                prd,
                design,
                note,
                authoritativeContractView,
                previousStateJson,
                progressSink
        );
    }

}
