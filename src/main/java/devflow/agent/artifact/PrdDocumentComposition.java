package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.executor.llm.ModelRole;
import java.nio.file.Path;

public final class PrdDocumentComposition implements DocumentCompositionStrategy {

    private final ContractExtractor contractExtractor;
    private final DocumentStageIntake intake;
    private final DocumentPromptAssembler promptAssembler;

    public PrdDocumentComposition(
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
        return StageType.PRD;
    }

    @Override
    public ModelRole modelRole() {
        return ModelRole.PRD;
    }

    @Override
    public int sourceMetadataSectionNumber() {
        return 8;
    }

    @Override
    public Integer contractMetadataSectionNumber() {
        return 7;
    }

    @Override
    public ConstraintSourceMetadata authoritativeSourceMetadata(Path projectPath, RunRecord runRecord) {
        String analysis = intake.requiredStageArtifact(projectPath, runRecord, StageType.ANALYSIS);
        return contractExtractor.buildAuthoritativeSourceMetadata(
                runRecord.goal(),
                runRecord.constraints(),
                analysis
        );
    }

    @Override
    public DocumentGenerationPrompt buildPrompt(Path projectPath, RunRecord runRecord, String note, DocumentDraftContext context) {
        String analysis = intake.requiredStageArtifact(projectPath, runRecord, StageType.ANALYSIS);
        String analysisForPrompt = intake.filterUpstreamPromptContext(
                analysis,
                StageType.ANALYSIS,
                intake.buildAuthorityCorpus(runRecord, null)
        );
        return promptAssembler.buildPrdPrompt(runRecord, note, context, analysisForPrompt);
    }

    @Override
    public DocumentCompositionContracts resolveContracts(Path projectPath, RunRecord runRecord, String merged) {
        ValidationMetadata validationMetadata = contractExtractor.extractValidationMetadata(merged);
        ExecutionContract executionContract = contractExtractor.extractExecutionContract(
                runRecord.goal(),
                runRecord.constraints(),
                merged,
                ""
        );
        return new DocumentCompositionContracts(
                contractExtractor.projectProductContractFromPrd(merged),
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
                contractExtractor.projectProductContractFromPrd(sanitized),
                contracts.executionContract(),
                contractExtractor.extractValidationMetadata(sanitized)
        );
    }
}
