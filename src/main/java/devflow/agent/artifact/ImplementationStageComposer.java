package devflow.agent.artifact;

import devflow.agent.context.ArtifactContextSanitizer;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.executor.ImplementationExecutionBundle;
import devflow.agent.executor.ImplementationExecutor;
import devflow.agent.executor.ImplementationProgressSink;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import java.nio.file.Path;

/**
 * implementation 阶段子门面。
 *
 * <p>负责 implementation 阶段的 intake、上下文准备、bundle 执行和产物落盘。
 * `StageArtifactComposer` 不再直接维护这些细节。
 */
final class ImplementationStageComposer {

    private final FileArtifactStore artifactStore;
    private final ImplementationExecutor implementationExecutor;
    private final ContractExtractor contractExtractor;
    private final StageArtifactInputResolver inputResolver;
    private final ImplementationArtifactPersister artifactPersister;

    ImplementationStageComposer(
            FileArtifactStore artifactStore,
            ImplementationExecutor implementationExecutor,
            ContractExtractor contractExtractor,
            StageArtifactInputResolver inputResolver,
            ImplementationArtifactPersister artifactPersister
    ) {
        this.artifactStore = artifactStore;
        this.implementationExecutor = implementationExecutor;
        this.contractExtractor = contractExtractor;
        this.inputResolver = inputResolver;
        this.artifactPersister = artifactPersister;
    }

    String compose(Path projectPath, RunRecord runRecord, String note) {
        String analysis = inputResolver.requiredStageArtifact(projectPath, runRecord, StageType.ANALYSIS);
        String prd = inputResolver.requiredStageArtifact(projectPath, runRecord, StageType.PRD);
        String design = inputResolver.requiredStageArtifact(projectPath, runRecord, StageType.DESIGN);
        ContractView contractView = contractExtractor.extractContractView(runRecord.goal(), runRecord.constraints(), analysis, prd, design);
        int currentAttempt = runRecord.stageStates().get(StageType.IMPLEMENTATION).attempt();
        String previousImplementationState = artifactStore.readLatestAttemptScopedAuxiliaryArtifact(
                projectPath,
                runRecord.runId(),
                AuxiliaryArtifactNames.IMPLEMENTATION_STATE,
                currentAttempt - 1
        );
        if (previousImplementationState.isBlank()) {
            previousImplementationState = artifactStore.readAuxiliaryArtifact(
                    projectPath,
                    runRecord.runId(),
                    AuxiliaryArtifactNames.IMPLEMENTATION_STATE
            );
        }
        String authorityCorpus = inputResolver.buildAuthorityCorpus(runRecord, contractView.executionContract());
        String analysisForImplementation = ArtifactContextSanitizer.sanitizeForPrompt(analysis, StageType.ANALYSIS, authorityCorpus);
        String prdForImplementation = ArtifactContextSanitizer.sanitizeForPrompt(prd, StageType.PRD, authorityCorpus);
        String designForImplementation = ArtifactContextSanitizer.sanitizeForPrompt(design, StageType.DESIGN, authorityCorpus);
        ImplementationProgressSink progressSink = bundle -> artifactPersister.persist(projectPath, runRecord, bundle);
        ImplementationExecutionBundle bundle = implementationExecutor.execute(
                projectPath,
                runRecord,
                analysisForImplementation,
                prdForImplementation,
                designForImplementation,
                note,
                contractView,
                previousImplementationState,
                progressSink
        );
        artifactPersister.persist(projectPath, runRecord, bundle);
        return bundle.implementationMarkdown();
    }
}
