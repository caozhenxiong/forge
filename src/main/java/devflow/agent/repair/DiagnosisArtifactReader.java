package devflow.agent.repair;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import java.nio.file.Path;
import java.nio.file.Files;

/**
 * 读取 diagnosis 需要的补充证据。
 *
 * <p>这层只负责从 artifact store 取最新测试相关产物，
 * 避免 DiagnosisAgent 再次承担 IO 和文件名协议细节。
 */
final class DiagnosisArtifactReader {

    private final FileArtifactStore artifactStore;

    DiagnosisArtifactReader(FileArtifactStore artifactStore) {
        this.artifactStore = artifactStore;
    }

    DiagnosisArtifactEvidence read(Path projectPath, RunRecord runRecord) {
        return new DiagnosisArtifactEvidence(
                artifactStore.readAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.TEST_RUNTIME_SNAPSHOT),
                artifactStore.readAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.TEST_EXECUTION),
                readOptionalStageArtifact(projectPath, runRecord, StageType.TEST)
        );
    }

    private String readOptionalStageArtifact(Path projectPath, RunRecord runRecord, StageType stageType) {
        Path artifactPath = artifactStore.artifactPath(projectPath, runRecord.runId(), stageType);
        if (!Files.exists(artifactPath)) {
            return "";
        }
        return artifactStore.readArtifact(projectPath, runRecord.runId(), stageType);
    }
}
