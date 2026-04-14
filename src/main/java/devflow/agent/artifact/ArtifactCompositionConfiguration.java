package devflow.agent.artifact;

import devflow.agent.context.ContractExtractor;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.prompt.PromptTemplateCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ArtifactCompositionConfiguration {

    @Bean
    DocumentDraftAssembler documentDraftAssembler() {
        return new DocumentDraftAssembler();
    }

    @Bean
    DocumentStageIntake documentStageIntake(
            ArtifactTemplateFactory artifactTemplateFactory,
            FileArtifactStore artifactStore,
            ContractExtractor contractExtractor,
            LanguagePolicy languagePolicy,
            DocumentDraftAssembler documentDraftAssembler
    ) {
        return new DocumentStageIntake(
                artifactTemplateFactory,
                artifactStore,
                contractExtractor,
                languagePolicy,
                documentDraftAssembler
        );
    }

    @Bean
    DocumentStagePostProcessor documentStagePostProcessor(
            ContractExtractor contractExtractor,
            DocumentDraftAssembler documentDraftAssembler
    ) {
        return new DocumentStagePostProcessor(contractExtractor, documentDraftAssembler);
    }

    @Bean
    DocumentPromptAssembler documentPromptAssembler(
            PromptTemplateCatalog promptTemplateCatalog,
            DocumentDraftAssembler documentDraftAssembler
    ) {
        return new DocumentPromptAssembler(promptTemplateCatalog, documentDraftAssembler);
    }

    @Bean
    DocumentGenerationSupport documentGenerationSupport(LlmProvider llmProvider) {
        return new DocumentGenerationSupport(llmProvider);
    }

    @Bean
    DocumentCompositionTemplate documentCompositionTemplate(
            ContractExtractor contractExtractor,
            DocumentStageIntake documentStageIntake,
            DocumentDraftAssembler documentDraftAssembler,
            DocumentStagePostProcessor documentStagePostProcessor,
            DocumentGenerationSupport documentGenerationSupport
    ) {
        return new DocumentCompositionTemplate(
                contractExtractor,
                documentStageIntake,
                documentDraftAssembler,
                documentStagePostProcessor,
                documentGenerationSupport
        );
    }

    @Bean
    AnalysisDocumentComposition analysisDocumentComposition(
            ContractExtractor contractExtractor,
            DocumentPromptAssembler documentPromptAssembler
    ) {
        return new AnalysisDocumentComposition(contractExtractor, documentPromptAssembler);
    }

    @Bean
    PrdDocumentComposition prdDocumentComposition(
            ContractExtractor contractExtractor,
            DocumentStageIntake documentStageIntake,
            DocumentPromptAssembler documentPromptAssembler
    ) {
        return new PrdDocumentComposition(contractExtractor, documentStageIntake, documentPromptAssembler);
    }

    @Bean
    DesignDocumentComposition designDocumentComposition(
            ContractExtractor contractExtractor,
            DocumentStageIntake documentStageIntake,
            DocumentPromptAssembler documentPromptAssembler
    ) {
        return new DesignDocumentComposition(contractExtractor, documentStageIntake, documentPromptAssembler);
    }

    @Bean
    DocumentStageComposer documentStageComposer(
            DocumentCompositionTemplate documentCompositionTemplate,
            AnalysisDocumentComposition analysisDocumentComposition,
            PrdDocumentComposition prdDocumentComposition,
            DesignDocumentComposition designDocumentComposition
    ) {
        return new DocumentStageComposer(
                documentCompositionTemplate,
                analysisDocumentComposition,
                prdDocumentComposition,
                designDocumentComposition
        );
    }
}
