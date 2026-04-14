package devflow.agent.review;

import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.gate.ArchitectIntegrationCheck;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.testing.TestExecutor;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.prompt.PromptTemplateCatalog;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;

public class StageReviewerHarness extends StageReviewer {

    public StageReviewerHarness(
            LlmProvider provider,
            WorkspaceSnapshotStore snapshotStore,
            TestExecutor testExecutor
    ) {
        super(
                documentStageReviewer(provider),
                implementationStageReviewer(provider, snapshotStore, testExecutor),
                executionStageReviewer()
        );
    }

    private static ContractExtractor contractExtractor() {
        return new ContractExtractor();
    }

    private static DocumentStructureGuard documentStructureGuard() {
        return new DocumentStructureGuard(contractExtractor());
    }

    private static DocumentReviewNormalizer documentReviewNormalizer() {
        return new DocumentReviewNormalizer(contractExtractor());
    }

    private static ImplementationReviewNormalizer implementationReviewNormalizer() {
        return new ImplementationReviewNormalizer(contractExtractor());
    }

    private static ReviewArtifactLoader reviewArtifactLoader() {
        return new ReviewArtifactLoader();
    }

    private static DocumentStageReviewer documentStageReviewer(LlmProvider provider) {
        ReviewArtifactLoader reviewArtifactLoader = reviewArtifactLoader();
        return new DocumentStageReviewer(
                new PromptTemplateCatalog(),
                new LanguagePolicy(),
                reviewArtifactLoader,
                documentReviewTurnExecutor(provider),
                documentStructureGuard()
        );
    }

    private static DocumentReviewTurnExecutor documentReviewTurnExecutor(LlmProvider provider) {
        DocumentReviewNormalizer normalizer = documentReviewNormalizer();
        return new DocumentReviewTurnExecutor(provider, new AgentTurnLoop(), normalizer);
    }

    private static ImplementationStageReviewer implementationStageReviewer(
            LlmProvider provider,
            WorkspaceSnapshotStore snapshotStore,
            TestExecutor testExecutor
    ) {
        ReviewArtifactLoader reviewArtifactLoader = reviewArtifactLoader();
        return new ImplementationStageReviewer(
                snapshotStore,
                testExecutor,
                reviewArtifactLoader,
                contractExtractor(),
                new ArchitectIntegrationCheck(new FileProjectWorkspace(), new TreeSitterSupport()),
                implementationReviewTurnExecutor(provider),
                new LanguagePolicy()
        );
    }

    private static ImplementationReviewTurnExecutor implementationReviewTurnExecutor(LlmProvider provider) {
        ImplementationReviewNormalizer normalizer = implementationReviewNormalizer();
        ReviewArtifactLoader reviewArtifactLoader = reviewArtifactLoader();
        return new ImplementationReviewTurnExecutor(provider, new AgentTurnLoop(), normalizer, reviewArtifactLoader);
    }

    private static ExecutionStageReviewer executionStageReviewer() {
        return new ExecutionStageReviewer(new ReviewDecisionArtifactParser());
    }
}
