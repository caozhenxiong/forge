package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.executor.llm.ModelRole;
import java.nio.file.Path;

interface DocumentCompositionStrategy {

    StageType stageType();

    ModelRole modelRole();

    int sourceMetadataSectionNumber();

    Integer contractMetadataSectionNumber();

    ConstraintSourceMetadata authoritativeSourceMetadata(Path projectPath, RunRecord runRecord);

    DocumentGenerationPrompt buildPrompt(Path projectPath, RunRecord runRecord, String note, DocumentDraftContext context);

    DocumentCompositionContracts resolveContracts(Path projectPath, RunRecord runRecord, String merged);

    DocumentCompositionContracts resolvePersistedContracts(
            Path projectPath,
            RunRecord runRecord,
            String sanitized,
            DocumentCompositionContracts contracts
    );
}
