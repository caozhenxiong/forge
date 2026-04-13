package devflow.agent.artifact;

import devflow.agent.domain.StageType;
import java.nio.file.Path;
import java.util.UUID;

public interface ArtifactStore {

    Path writeArtifact(UUID runId, StageType stageType, String content);

    String readArtifact(UUID runId, StageType stageType);
}

