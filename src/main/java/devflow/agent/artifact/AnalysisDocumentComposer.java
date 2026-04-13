package devflow.agent.artifact;

import devflow.agent.executor.llm.ModelRole;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;

/**
 * 负责 `ANALYSIS` 阶段文档的 intake / generate / sanitize。
 */
final class AnalysisDocumentComposer {

    private final ContractExtractor contractExtractor;
    private final DocumentStageIntake intake;
    private final DocumentDraftAssembler draftAssembler;
    private final DocumentStagePostProcessor postProcessor;
    private final DocumentPromptAssembler promptAssembler;
    private final DocumentGenerationSupport generationSupport;

    AnalysisDocumentComposer(
            ContractExtractor contractExtractor,
            DocumentStageIntake intake,
            DocumentDraftAssembler draftAssembler,
            DocumentStagePostProcessor postProcessor,
            DocumentPromptAssembler promptAssembler,
            DocumentGenerationSupport generationSupport
    ) {
        this.contractExtractor = contractExtractor;
        this.intake = intake;
        this.draftAssembler = draftAssembler;
        this.postProcessor = postProcessor;
        this.promptAssembler = promptAssembler;
        this.generationSupport = generationSupport;
    }

    String compose(RunRecord runRecord, String note) {
        DocumentLanguage language = intake.documentLanguage(runRecord, note);
        ConstraintSourceMetadata authoritativeSourceMetadata = contractExtractor.buildAuthoritativeSourceMetadata(
                runRecord.goal(),
                runRecord.constraints()
        );
        DocumentDraftContext context = intake.buildContext(
                StageType.ANALYSIS,
                runRecord,
                note,
                language,
                authoritativeSourceMetadata
        );
        DocumentGenerationPrompt prompt = promptAssembler.buildAnalysisPrompt(runRecord, note, context);
        String generated = generationSupport.generate(prompt, context.mode(), ModelRole.ANALYSIS);
        generated = postProcessor.stabilizeSourceMetadata(generated, context.authoritativeSourceMetadata(), 7, language);
        String merged = draftAssembler.mergeDocumentDraft(StageType.ANALYSIS, context.previousDraft(), generated, context.targetSections());
        merged = postProcessor.stripMachineBlocks(merged);
        String sanitized = postProcessor.sanitizeDocumentConstraintEscalation(
                StageType.ANALYSIS,
                merged,
                context.authoritativeSourceMetadata(),
                null,
                language
        );
        return postProcessor.upsertDocumentBlocks(
                sanitized,
                contractExtractor.extractConstraintSourceMetadata(sanitized),
                null,
                null,
                null
        );
    }
}
