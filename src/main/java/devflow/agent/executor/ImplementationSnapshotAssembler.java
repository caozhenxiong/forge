package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责 implementation 阶段运行时快照的装配。
 *
 * <p>这层只做三件事：
 * 1. 把 plan/subtask/report 组装成 task package 与 worker result；
 * 2. 生成单一状态源 {@link ImplementationRuntimeSnapshot}；
 * 3. 基于快照渲染最终 bundle，并向进度 sink 发布中间态。
 *
 * <p>它不负责 planning、子任务执行、阶段 gate，也不决定流程如何推进。
 */
class ImplementationSnapshotAssembler {

    private final ImplementationArtifactRenderer implementationArtifactRenderer;
    private final TaskPackageAssembler taskPackageAssembler;
    private final WorkerResultAssembler workerResultAssembler;

    ImplementationSnapshotAssembler(
            ImplementationArtifactRenderer implementationArtifactRenderer,
            TargetedFileContextRenderer targetedFileContextRenderer
    ) {
        this.implementationArtifactRenderer = implementationArtifactRenderer;
        this.taskPackageAssembler = new TaskPackageAssembler(targetedFileContextRenderer);
        this.workerResultAssembler = new WorkerResultAssembler();
    }

    List<TaskPackage> buildTaskPackages(
            Path projectPath,
            ImplementationPlan plan,
            SharedContextBundle sharedContextBundle,
            ContractView contractView,
            ProjectFingerprint fingerprint
    ) {
        return taskPackageAssembler.buildTaskPackages(projectPath, plan, sharedContextBundle, contractView, fingerprint);
    }

    void publishSnapshot(
            ImplementationProgressSink progressSink,
            ImplementationPlan plan,
            List<TaskPackage> taskPackages,
            List<SubtaskExecutionReport> reports,
            List<ImplementationEventEntry> events,
            String note,
            DocumentLanguage language,
            DeliveryPolicyEnvelope deliveryPolicy,
            SharedContextBundle sharedContextBundle,
            ImplementationStageStatus stageStatus,
            ArchitectIntegrationCheckResult architectCheckResult,
            String currentSubtaskTitle
    ) {
        // 中间态和最终态必须走同一套渲染链，避免不同 artifact 出现不同真相。
        if (progressSink == null) {
            return;
        }
        progressSink.publish(buildBundle(buildSnapshot(
                plan,
                reports,
                events,
                note,
                language,
                deliveryPolicy,
                sharedContextBundle,
                taskPackages,
                stageStatus,
                architectCheckResult,
                currentSubtaskTitle
        )));
    }

    ImplementationRuntimeSnapshot buildSnapshot(
            ImplementationPlan plan,
            List<SubtaskExecutionReport> reports,
            List<ImplementationEventEntry> events,
            String note,
            DocumentLanguage language,
            DeliveryPolicyEnvelope deliveryPolicy,
            SharedContextBundle sharedContextBundle,
            List<TaskPackage> taskPackages,
            ImplementationStageStatus stageStatus,
            ArchitectIntegrationCheckResult architectCheckResult,
            String currentSubtaskTitle
    ) {
        // worker result 始终从 report 派生，避免 execution 阶段再维护一份平行状态。
        return new ImplementationRuntimeSnapshot(
                plan,
                taskPackages,
                reports,
                workerResultAssembler.buildWorkerResults(plan, reports, currentSubtaskTitle),
                events == null ? List.of() : List.copyOf(events),
                note,
                language,
                deliveryPolicy,
                sharedContextBundle,
                stageStatus,
                architectCheckResult,
                currentSubtaskTitle
        );
    }

    ImplementationRuntimeSnapshot buildSnapshot(
            ImplementationPlan plan,
            List<SubtaskExecutionReport> reports,
            List<ImplementationEventEntry> events,
            String note,
            DocumentLanguage language,
            DeliveryPolicyEnvelope deliveryPolicy,
            SharedContextBundle sharedContextBundle,
            List<TaskPackage> taskPackages,
            ImplementationStageStatus stageStatus,
            String currentSubtaskTitle
    ) {
        return buildSnapshot(
                plan,
                reports,
                events,
                note,
                language,
                deliveryPolicy,
                sharedContextBundle,
                taskPackages,
                stageStatus,
                null,
                currentSubtaskTitle
        );
    }

    ImplementationExecutionBundle buildBundle(ImplementationRuntimeSnapshot snapshot) {
        return new ImplementationExecutionBundle(
                implementationArtifactRenderer.renderReport(snapshot),
                implementationArtifactRenderer.renderBacklog(
                        snapshot.plan(),
                        snapshot.deliveryPolicy(),
                        snapshot.sharedContextBundle(),
                        snapshot.language()
                ),
                implementationArtifactRenderer.renderRepairAlignment(
                        snapshot.reports(),
                        snapshot.note(),
                        snapshot.deliveryPolicy(),
                        snapshot.language()
                ),
                snapshot.sharedContextBundle() == null ? "" : snapshot.sharedContextBundle().toMarkdown(snapshot.language()),
                implementationArtifactRenderer.renderTaskPackages(snapshot.taskPackages(), snapshot.language()),
                implementationArtifactRenderer.renderWorkerResults(snapshot),
                implementationArtifactRenderer.renderEvents(snapshot),
                implementationArtifactRenderer.renderProgress(snapshot),
                implementationArtifactRenderer.renderStateJson(snapshot),
                snapshot
        );
    }
}
