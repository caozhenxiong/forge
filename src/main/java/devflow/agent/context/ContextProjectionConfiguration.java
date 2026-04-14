package devflow.agent.context;

import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.project.FileProjectWorkspace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ContextProjectionConfiguration {

    @Bean
    ContextProjectionArtifactReader contextProjectionArtifactReader(
            FileArtifactStore artifactStore,
            FileProjectWorkspace workspace
    ) {
        return new ContextProjectionArtifactReader(artifactStore, workspace);
    }

    @Bean
    ContextProjectionContractResolver contextProjectionContractResolver(
            ContractExtractor contractExtractor,
            LanguagePolicy languagePolicy
    ) {
        return new ContextProjectionContractResolver(contractExtractor, languagePolicy);
    }

    @Bean
    ContextProjectionSummaryAssembler contextProjectionSummaryAssembler(ArtifactSummaryBuilder artifactSummaryBuilder) {
        return new ContextProjectionSummaryAssembler(artifactSummaryBuilder);
    }

    @Bean
    ContextProjectionAssembler contextProjectionAssembler(ContextLayerAssembler contextLayerAssembler) {
        return new ContextProjectionAssembler(contextLayerAssembler);
    }
}
