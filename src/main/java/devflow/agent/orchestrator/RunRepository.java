package devflow.agent.orchestrator;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public interface RunRepository {

    void initialize(Path projectPath);

    RunRecord save(RunRecord runRecord);

    Optional<RunRecord> findById(Path projectPath, UUID runId);
}

