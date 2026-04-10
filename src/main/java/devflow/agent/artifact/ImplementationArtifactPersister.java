package devflow.agent.artifact;

import devflow.agent.executor.ImplementationExecutionBundle;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import java.nio.file.Path;

/**
 * implementation 阶段产物持久化门面。
 *
 * <p>把 implementation bundle 如何落盘从阶段路由器里抽出来，避免主流程继续直接写一串 artifact key。
 */
final class ImplementationArtifactPersister {

    private final FileArtifactStore artifactStore;

    ImplementationArtifactPersister(FileArtifactStore artifactStore) {
        this.artifactStore = artifactStore;
    }

    void persist(Path projectPath, RunRecord runRecord, ImplementationExecutionBundle bundle) {
        int currentAttempt = runRecord.stageStates().get(StageType.IMPLEMENTATION).attempt();
        artifactStore.writeArtifact(projectPath, runRecord.runId(), StageType.IMPLEMENTATION, bundle.implementationMarkdown());
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.IMPLEMENTATION_BACKLOG, bundle.backlogMarkdown());
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.REPAIR_ALIGNMENT, bundle.repairAlignmentMarkdown());
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.IMPLEMENTATION_SHARED_CONTEXT, bundle.sharedContextMarkdown());
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.TASK_PACKAGES, bundle.taskPackagesMarkdown());
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.WORKER_RESULTS, bundle.workerResultsMarkdown());
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.IMPLEMENTATION_EVENTS, bundle.eventsMarkdown());
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), AuxiliaryArtifactNames.IMPLEMENTATION_PROGRESS, bundle.progressMarkdown());
        artifactStore.writeAttemptScopedAuxiliaryArtifact(
                projectPath,
                runRecord.runId(),
                AuxiliaryArtifactNames.IMPLEMENTATION_STATE,
                currentAttempt,
                bundle.stateJson()
        );
    }
}
