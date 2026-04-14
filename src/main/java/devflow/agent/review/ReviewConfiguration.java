package devflow.agent.review;

import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.gate.ArchitectIntegrationCheck;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.testing.TestExecutor;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.prompt.PromptTemplateCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ReviewConfiguration {

    @Bean
    ReviewDecisionArtifactParser reviewDecisionArtifactParser() {
        return new ReviewDecisionArtifactParser();
    }

    @Bean
    DocumentStructureGuard documentStructureGuard(ContractExtractor contractExtractor) {
        return new DocumentStructureGuard(contractExtractor);
    }

    @Bean
    DocumentReviewNormalizer documentReviewNormalizer(ContractExtractor contractExtractor) {
        return new DocumentReviewNormalizer(contractExtractor);
    }

    @Bean
    ImplementationReviewNormalizer implementationReviewNormalizer(ContractExtractor contractExtractor) {
        return new ImplementationReviewNormalizer(contractExtractor);
    }

    @Bean
    ReviewArtifactLoader reviewArtifactLoader() {
        return new ReviewArtifactLoader();
    }

    @Bean
    DocumentReviewTurnExecutor documentReviewTurnExecutor(
            LlmProvider llmProvider,
            AgentTurnLoop agentTurnLoop,
            DocumentReviewNormalizer documentReviewNormalizer
    ) {
        return new DocumentReviewTurnExecutor(llmProvider, agentTurnLoop, documentReviewNormalizer);
    }

    @Bean
    ImplementationReviewTurnExecutor implementationReviewTurnExecutor(
            LlmProvider llmProvider,
            AgentTurnLoop agentTurnLoop,
            ImplementationReviewNormalizer implementationReviewNormalizer,
            ReviewArtifactLoader reviewArtifactLoader
    ) {
        return new ImplementationReviewTurnExecutor(
                llmProvider,
                agentTurnLoop,
                implementationReviewNormalizer,
                reviewArtifactLoader
        );
    }

    @Bean
    DocumentStageReviewer documentStageReviewer(
            PromptTemplateCatalog promptTemplateCatalog,
            LanguagePolicy languagePolicy,
            ReviewArtifactLoader reviewArtifactLoader,
            DocumentReviewTurnExecutor documentReviewTurnExecutor,
            DocumentStructureGuard documentStructureGuard
    ) {
        return new DocumentStageReviewer(
                promptTemplateCatalog,
                languagePolicy,
                reviewArtifactLoader,
                documentReviewTurnExecutor,
                documentStructureGuard
        );
    }

    @Bean
    ImplementationStageReviewer implementationStageReviewer(
            WorkspaceSnapshotStore snapshotStore,
            TestExecutor testExecutor,
            ReviewArtifactLoader reviewArtifactLoader,
            ContractExtractor contractExtractor,
            ArchitectIntegrationCheck architectIntegrationCheck,
            ImplementationReviewTurnExecutor implementationReviewTurnExecutor,
            LanguagePolicy languagePolicy
    ) {
        return new ImplementationStageReviewer(
                snapshotStore,
                testExecutor,
                reviewArtifactLoader,
                contractExtractor,
                architectIntegrationCheck,
                implementationReviewTurnExecutor,
                languagePolicy
        );
    }

    @Bean
    ExecutionStageReviewer executionStageReviewer(ReviewDecisionArtifactParser reviewDecisionArtifactParser) {
        return new ExecutionStageReviewer(reviewDecisionArtifactParser);
    }
}
