package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ExecutionContract;
import devflow.agent.executor.ModelRole;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import java.nio.file.Path;

/**
 * 负责 `DESIGN` 阶段文档的 intake / generate / sanitize。
 */
final class DesignDocumentComposer {

    private final ContractExtractor contractExtractor;
    private final DocumentStageIntake intake;
    private final DocumentDraftAssembler draftAssembler;
    private final DocumentStagePostProcessor postProcessor;
    private final DocumentPromptAssembler promptAssembler;
    private final DocumentGenerationSupport generationSupport;

    DesignDocumentComposer(
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
        String prd = intake.requiredStageArtifact(projectPath, runRecord, StageType.PRD);
        String analysis = intake.currentStageArtifactOrEmpty(runRecord, StageType.ANALYSIS);
        ConstraintSourceMetadata authoritativeSourceMetadata = contractExtractor.buildAuthoritativeSourceMetadata(
                runRecord.goal(),
                runRecord.constraints(),
                analysis,
                prd
        );
        DocumentDraftContext context = intake.buildContext(
                StageType.DESIGN,
                runRecord,
                note,
                language,
                authoritativeSourceMetadata
        );
        String prdForPrompt = intake.filterUpstreamPromptContext(
                prd,
                StageType.PRD,
                intake.buildAuthorityCorpus(runRecord, null)
        );
        DocumentGenerationPrompt prompt = promptAssembler.buildDesignPrompt(runRecord, note, context, prdForPrompt);
        String generated = generationSupport.generate(prompt, context.mode(), ModelRole.DESIGN);
        generated = postProcessor.stabilizeSourceMetadata(generated, context.authoritativeSourceMetadata(), 9, language);
        String merged = draftAssembler.mergeDocumentDraft(StageType.DESIGN, context.previousDraft(), generated, context.targetSections());
        ExecutionContract executionContract = contractExtractor.extractExecutionContract(
                runRecord.goal(),
                runRecord.constraints(),
                prd,
                merged
        );
        merged = postProcessor.stabilizeExecutionContractMetadata(merged, executionContract, 8);
        String sanitized = postProcessor.sanitizeDocumentConstraintEscalation(
                runRecord,
                StageType.DESIGN,
                merged,
                context.authoritativeSourceMetadata(),
                executionContract,
                language
        );
        return postProcessor.upsertDocumentBlocks(
                sanitized,
                context.authoritativeSourceMetadata(),
                null,
                executionContract,
                contractExtractor.extractValidationMetadata(sanitized)
        );
    }
}
