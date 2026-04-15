package devflow.agent.orchestrator;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.domain.RunRecord;
import devflow.agent.executor.implementation.state.ImplementationStateArtifactSupport;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import java.nio.file.Path;

/**
 * 统一处理 implementation_state 读取、阶段就绪判断与 continuation 展开。
 */
public class ImplementationProgressSupport {

    private final FileArtifactStore artifactStore;
    private final ImplementationStateArtifactSupport implementationStateArtifactSupport;
    private final ImplementationContinuationSupport implementationContinuationSupport;

    public ImplementationProgressSupport(
            FileArtifactStore artifactStore,
            ImplementationStateArtifactSupport implementationStateArtifactSupport,
            ImplementationContinuationSupport implementationContinuationSupport
    ) {
        this.artifactStore = artifactStore;
        this.implementationStateArtifactSupport = implementationStateArtifactSupport;
        this.implementationContinuationSupport = implementationContinuationSupport;
    }

    public ImplementationProgressState read(Path projectPath, RunRecord current) {
        String implementationStateJson = readImplementationStateArtifact(projectPath, current);
        ImplementationStageStatusPayload stageStatus = implementationStateArtifactSupport.readStageStatus(implementationStateJson);
        if (stageStatus.stageReady()) {
            return ImplementationProgressState.ready(
                    implementationStateArtifactSupport.renderImplementationReviewSummary(implementationStateJson)
            );
        }
        StageContinuationContext continuationContext = implementationContinuationSupport.toContinuationContext(stageStatus);
        if (stageStatus.continuationMode().blocked()) {
            return ImplementationProgressState.blocked(
                    continuationContext,
                    implementationContinuationSupport.toHumanReviewResult(continuationContext)
            );
        }
        return ImplementationProgressState.continuing(continuationContext);
    }

    public ImplementationRevisionFacts readRevisionFacts(Path projectPath, RunRecord current) {
        String implementationStateJson;
        try {
            implementationStateJson = readImplementationStateArtifact(projectPath, current);
        } catch (IllegalStateException ignored) {
            return ImplementationRevisionFacts.none();
        }
        ImplementationStageStatusPayload stageStatus = implementationStateArtifactSupport.readStageStatus(implementationStateJson);
        if (stageStatus.stageReady()) {
            return ImplementationRevisionFacts.none();
        }
        StageContinuationContext continuationContext = implementationContinuationSupport.toContinuationContext(stageStatus);
        return new ImplementationRevisionFacts(
                false,
                stageStatus.continuationMode().blocked(),
                stageStatus.incompleteSubtasks(),
                continuationContext
        );
    }

    private String readImplementationStateArtifact(Path projectPath, RunRecord current) {
        String content = artifactStore.readAuxiliaryArtifact(
                projectPath,
                current.runId(),
                AuxiliaryArtifactNames.IMPLEMENTATION_STATE
        );
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Missing implementation_state auxiliary artifact.");
        }
        return content;
    }
}
