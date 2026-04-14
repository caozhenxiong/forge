package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.executor.llm.ModelRole;
import java.nio.file.Path;

public final class DesignDocumentComposition implements DocumentCompositionStrategy {

    private final ContractExtractor contractExtractor;
    private final DocumentStageIntake intake;
    private final DocumentPromptAssembler promptAssembler;

    public DesignDocumentComposition(
            ContractExtractor contractExtractor,
            DocumentStageIntake intake,
            DocumentPromptAssembler promptAssembler
    ) {
        this.contractExtractor = contractExtractor;
        this.intake = intake;
        this.promptAssembler = promptAssembler;
    }

    @Override
    public StageType stageType() {
        return StageType.DESIGN;
    }

    @Override
    public ModelRole modelRole() {
        return ModelRole.DESIGN;
    }

    @Override
    public int sourceMetadataSectionNumber() {
        return 9;
    }

    @Override
    public Integer contractMetadataSectionNumber() {
        return 8;
    }

    @Override
    public ConstraintSourceMetadata authoritativeSourceMetadata(Path projectPath, RunRecord runRecord) {
        String prd = intake.requiredStageArtifact(projectPath, runRecord, StageType.PRD);
        String analysis = intake.currentStageArtifactOrEmpty(runRecord, StageType.ANALYSIS);
        return contractExtractor.buildAuthoritativeSourceMetadata(
                runRecord.goal(),
                runRecord.constraints(),
                analysis,
                prd
        );
    }

    @Override
    public DocumentGenerationPrompt buildPrompt(Path projectPath, RunRecord runRecord, String note, DocumentDraftContext context) {
        String prd = intake.requiredStageArtifact(projectPath, runRecord, StageType.PRD);
        String prdForPrompt = intake.filterUpstreamPromptContext(
                prd,
                StageType.PRD,
                intake.buildAuthorityCorpus(runRecord, null)
        );
        return promptAssembler.buildDesignPrompt(runRecord, note, context, prdForPrompt);
    }

    @Override
    public DocumentCompositionContracts resolveContracts(Path projectPath, RunRecord runRecord, String merged) {
        String prd = intake.requiredStageArtifact(projectPath, runRecord, StageType.PRD);
        ValidationMetadata validationMetadata = contractExtractor.extractValidationMetadata(merged);
        ExecutionContract executionContract = contractExtractor.extractExecutionContract(
                runRecord.goal(),
                runRecord.constraints(),
                prd,
                merged
        );
        return new DocumentCompositionContracts(
                null,
                executionContract,
                validationMetadata
        );
    }

    @Override
    public DocumentCompositionContracts resolvePersistedContracts(
            Path projectPath,
            RunRecord runRecord,
            String sanitized,
            DocumentCompositionContracts contracts
    ) {
        return new DocumentCompositionContracts(
                null,
                contracts.executionContract(),
                contractExtractor.extractValidationMetadata(sanitized)
        );
    }
}
