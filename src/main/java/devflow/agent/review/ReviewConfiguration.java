package devflow.agent.review;

import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.gate.ArchitectIntegrationCheck;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.loop.AgentTurnLoop;
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
}
