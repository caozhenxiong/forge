package devflow.agent.artifact;

import devflow.agent.executor.llm.ModelRole;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import java.nio.file.Path;

/**
 * 负责 `PRD` 阶段文档的 intake / generate / sanitize。
 */
final class PrdDocumentComposer {

    private final ContractExtractor contractExtractor;
    private final DocumentStageIntake intake;
    private final DocumentDraftAssembler draftAssembler;
    private final DocumentStagePostProcessor postProcessor;
    private final DocumentPromptAssembler promptAssembler;
    private final DocumentGenerationSupport generationSupport;

    PrdDocumentComposer(
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

    String compose(Path projectPath, RunRecord runRecord, String note) {
        DocumentLanguage language = intake.documentLanguage(runRecord, note);
        String analysis = intake.requiredStageArtifact(projectPath, runRecord, StageType.ANALYSIS);
        ConstraintSourceMetadata authoritativeSourceMetadata = contractExtractor.buildAuthoritativeSourceMetadata(
                runRecord.goal(),
                runRecord.constraints(),
                analysis
        );
        DocumentDraftContext context = intake.buildContext(
                StageType.PRD,
                runRecord,
                note,
                language,
                authoritativeSourceMetadata
        );
        String analysisForPrompt = intake.filterUpstreamPromptContext(
                analysis,
                StageType.ANALYSIS,
                intake.buildAuthorityCorpus(runRecord, null)
        );
        DocumentGenerationPrompt prompt = promptAssembler.buildPrdPrompt(runRecord, note, context, analysisForPrompt);
        String generated = generationSupport.generate(prompt, context.mode(), ModelRole.PRD);
        generated = postProcessor.stabilizeSourceMetadata(generated, context.authoritativeSourceMetadata(), 8, language);
        String merged = draftAssembler.mergeDocumentDraft(StageType.PRD, context.previousDraft(), generated, context.targetSections());
        merged = postProcessor.stripMachineBlocks(merged);
        ValidationMetadata validationMetadata = contractExtractor.extractValidationMetadata(merged);
        ExecutionContract executionContract = contractExtractor.extractExecutionContract(
                runRecord.goal(),
                runRecord.constraints(),
                merged,
                ""
        );
        merged = postProcessor.stabilizeContractMetadata(merged, executionContract, validationMetadata, 7);
        String sanitized = postProcessor.sanitizeDocumentConstraintEscalation(
                StageType.PRD,
                merged,
                context.authoritativeSourceMetadata(),
                validationMetadata,
                language
        );
        return postProcessor.upsertDocumentBlocks(
                sanitized,
                contractExtractor.extractConstraintSourceMetadata(sanitized),
                contractExtractor.projectProductContractFromPrd(sanitized),
                executionContract,
                contractExtractor.extractValidationMetadata(sanitized)
        );
    }
}
