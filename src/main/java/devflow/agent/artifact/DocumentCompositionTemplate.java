package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import java.nio.file.Path;

public final class DocumentCompositionTemplate {

    private final ContractExtractor contractExtractor;
    private final DocumentStageIntake intake;
    private final DocumentDraftAssembler draftAssembler;
    private final DocumentStagePostProcessor postProcessor;
    private final DocumentGenerationSupport generationSupport;

    public DocumentCompositionTemplate(
            ContractExtractor contractExtractor,
            DocumentStageIntake intake,
            DocumentDraftAssembler draftAssembler,
            DocumentStagePostProcessor postProcessor,
            DocumentGenerationSupport generationSupport
    ) {
        this.contractExtractor = contractExtractor;
        this.intake = intake;
        this.draftAssembler = draftAssembler;
        this.postProcessor = postProcessor;
        this.generationSupport = generationSupport;
    }

    String compose(Path projectPath, RunRecord runRecord, String note, DocumentCompositionStrategy strategy) {
        StageType stageType = strategy.stageType();
        DocumentLanguage language = intake.documentLanguage(runRecord, note);
        ConstraintSourceMetadata authoritativeSourceMetadata = strategy.authoritativeSourceMetadata(projectPath, runRecord);
        DocumentDraftContext context = intake.buildContext(
                stageType,
                runRecord,
                note,
                language,
                authoritativeSourceMetadata
        );
        DocumentGenerationPrompt prompt = strategy.buildPrompt(projectPath, runRecord, note, context);
        String generated = generationSupport.generate(prompt, context.mode(), strategy.modelRole());
        generated = postProcessor.stabilizeSourceMetadata(
                generated,
                context.authoritativeSourceMetadata(),
                strategy.sourceMetadataSectionNumber(),
                language
        );
        String merged = draftAssembler.mergeDocumentDraft(stageType, context.previousDraft(), generated, context.targetSections());
        merged = postProcessor.stripMachineBlocks(merged);
        DocumentCompositionContracts contracts = strategy.resolveContracts(projectPath, runRecord, merged);
        if (strategy.contractMetadataSectionNumber() != null) {
            merged = postProcessor.stabilizeContractMetadata(
                    merged,
                    contracts.executionContract(),
                    contracts.validationMetadata(),
                    strategy.contractMetadataSectionNumber()
            );
        }
        String sanitized = postProcessor.sanitizeDocumentConstraintEscalation(
                stageType,
                merged,
                context.authoritativeSourceMetadata(),
                contracts.validationMetadata(),
                language
        );
        DocumentCompositionContracts persistedContracts = strategy.resolvePersistedContracts(
                projectPath,
                runRecord,
                sanitized,
                contracts
        );
        return postProcessor.upsertDocumentBlocks(
                sanitized,
                contractExtractor.extractConstraintSourceMetadata(sanitized),
                persistedContracts.productContract(),
                persistedContracts.executionContract(),
                persistedContracts.validationMetadata()
        );
    }
}
