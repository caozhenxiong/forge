package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.executor.llm.ModelRole;
import java.nio.file.Path;

public final class AnalysisDocumentComposition implements DocumentCompositionStrategy {

    private final ContractExtractor contractExtractor;
    private final DocumentPromptAssembler promptAssembler;

    public AnalysisDocumentComposition(
            ContractExtractor contractExtractor,
            DocumentPromptAssembler promptAssembler
    ) {
        this.contractExtractor = contractExtractor;
        this.promptAssembler = promptAssembler;
    }

    @Override
    public StageType stageType() {
        return StageType.ANALYSIS;
    }

    @Override
    public ModelRole modelRole() {
        return ModelRole.ANALYSIS;
    }

    @Override
    public int sourceMetadataSectionNumber() {
        return 7;
    }

    @Override
    public Integer contractMetadataSectionNumber() {
        return null;
    }

    @Override
    public ConstraintSourceMetadata authoritativeSourceMetadata(Path projectPath, RunRecord runRecord) {
        return contractExtractor.buildAuthoritativeSourceMetadata(
                runRecord.goal(),
                runRecord.constraints()
        );
    }

    @Override
    public DocumentGenerationPrompt buildPrompt(Path projectPath, RunRecord runRecord, String note, DocumentDraftContext context) {
        return promptAssembler.buildAnalysisPrompt(runRecord, note, context);
    }

    @Override
    public DocumentCompositionContracts resolveContracts(Path projectPath, RunRecord runRecord, String merged) {
        return DocumentCompositionContracts.empty();
    }

    @Override
    public DocumentCompositionContracts resolvePersistedContracts(
            Path projectPath,
            RunRecord runRecord,
            String sanitized,
            DocumentCompositionContracts contracts
    ) {
        return DocumentCompositionContracts.empty();
    }
}
