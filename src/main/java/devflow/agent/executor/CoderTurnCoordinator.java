package devflow.agent.executor;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ContractView;
import devflow.agent.orchestrator.RunRecord;
import java.nio.file.Path;
import java.util.List;

/**
 * Coder 角色的外层 turn 编排器。
 *
 * <p>它负责 implementation 阶段的一次完整 coder turn：
 * 1. 解析 execution context
 * 2. 复用上一轮状态
 * 3. 规划 subtasks
 * 4. 逐步执行计划并持续发布快照
 * 5. 做最终 gate 并生成 bundle
 *
 * <p>这样 `ImplementationExecutor` 就不再继续隐式扮演 coder 角色本身，而只保留
 * 对外兼容门面。
 */
final class CoderTurnCoordinator {

    private final EventLogStore eventLogStore;
    private final FileArtifactStore fileArtifactStore;
    private final ImplementationStageGate implementationStageGate;
    private final ImplementationGateEngine implementationGateEngine;
    private final ImplementationResumePolicy implementationResumePolicy;
    private final ImplementationPlanningRetryPolicy implementationPlanningRetryPolicy;
    private final ImplementationPlanRunner implementationPlanRunner;
    private final ImplementationSnapshotAssembler implementationSnapshotAssembler;
    private final ImplementationContextResolver implementationContextResolver;

    CoderTurnCoordinator(
            EventLogStore eventLogStore,
            FileArtifactStore fileArtifactStore,
            ImplementationStageGate implementationStageGate,
            ImplementationGateEngine implementationGateEngine,
            ImplementationResumePolicy implementationResumePolicy,
            ImplementationPlanningRetryPolicy implementationPlanningRetryPolicy,
            ImplementationPlanRunner implementationPlanRunner,
            ImplementationSnapshotAssembler implementationSnapshotAssembler,
            ImplementationContextResolver implementationContextResolver
    ) {
        this.eventLogStore = eventLogStore;
        this.fileArtifactStore = fileArtifactStore;
        this.implementationStageGate = implementationStageGate;
        this.implementationGateEngine = implementationGateEngine;
        this.implementationResumePolicy = implementationResumePolicy;
        this.implementationPlanningRetryPolicy = implementationPlanningRetryPolicy;
        this.implementationPlanRunner = implementationPlanRunner;
        this.implementationSnapshotAssembler = implementationSnapshotAssembler;
        this.implementationContextResolver = implementationContextResolver;
    }

    ImplementationExecutionBundle execute(
            Path projectPath,
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            ContractView authoritativeContractView,
            String previousStateJson,
            ImplementationProgressSink progressSink
    ) {
        ImplementationExecutionContext executionContext = implementationContextResolver.resolve(
                projectPath,
                runRecord,
                analysis,
                prd,
                design,
                note,
                authoritativeContractView,
                previousStateJson
        );
        ImplementationEventJournal eventJournal = new ImplementationEventJournal(
                eventLogStore,
                fileArtifactStore,
                projectPath,
                runRecord
        );
        ReusableImplementationState reusableState = implementationResumePolicy.loadReusableImplementationState(
                previousStateJson,
                executionContext.fixMode(),
                executionContext.implementationPatchTarget(),
                executionContext.overrideChanges(),
                executionContext.language()
        );
        if (reusableState != null) {
            eventJournal.append(ImplementationEventMessages.reusingPreviousPlan(
                    reusableState.completedReports().size(),
                    reusableState.plan().subtasks().size()
            ));
        }
        ImplementationPlan plan = reusableState == null
                ? implementationPlanningRetryPolicy.planWithInternalRetries(
                projectPath,
                runRecord,
                analysis,
                prd,
                design,
                note,
                executionContext.workspaceContext(),
                executionContext.plannerContextMarkdown(),
                executionContext.performanceValidationGuidance(),
                executionContext.preferSkeletonFlow(),
                executionContext.deliveryPolicy(),
                executionContext.contractView(),
                executionContext.qualityPlan(),
                executionContext.fingerprint(),
                executionContext.language(),
                executionContext.fixMode(),
                executionContext.implementationPatchTarget(),
                executionContext.authoritativeCoverageCatalog(),
                executionContext.continuationConstraints(),
                eventJournal::append
        )
                : reusableState.plan();
        List<TaskPackage> taskPackages = implementationSnapshotAssembler.buildTaskPackages(
                projectPath,
                plan,
                executionContext.sharedContextBundle(),
                executionContext.contractView(),
                executionContext.fingerprint()
        );
        implementationSnapshotAssembler.publishSnapshot(
                progressSink,
                plan,
                taskPackages,
                List.of(),
                eventJournal.snapshot(),
                note,
                executionContext.language(),
                executionContext.deliveryPolicy(),
                executionContext.sharedContextBundle(),
                new ImplementationStageStatus(
                        plan.subtasks().size(),
                        0,
                        0,
                        false,
                        false,
                        false,
                        plan.subtasks().stream().map(Subtask::title).toList()
                ),
                null,
                null
        );
        List<SubtaskExecutionReport> reports = implementationPlanRunner.execute(
                projectPath,
                runRecord,
                note,
                plan,
                taskPackages,
                executionContext.deliveryPolicy(),
                executionContext.contractView(),
                executionContext.qualityPlan(),
                executionContext.fingerprint(),
                executionContext.language(),
                reusableState == null ? List.of() : reusableState.completedReports(),
                reusableState == null ? null : reusableState.resumedExecutionState(),
                executionContext.sharedContextBundle(),
                executionContext.coderContextMarkdown(),
                (currentReports, currentSubtaskTitle) -> implementationSnapshotAssembler.publishSnapshot(
                        progressSink,
                        plan,
                        taskPackages,
                        currentReports,
                        eventJournal.snapshot(),
                        note,
                        executionContext.language(),
                        executionContext.deliveryPolicy(),
                        executionContext.sharedContextBundle(),
                        implementationStageGate.summarizeStageStatus(plan, currentReports, true),
                        null,
                        currentSubtaskTitle
                ),
                eventJournal
        );
        ImplementationGateOutcome gateOutcome = implementationGateEngine.evaluate(
                projectPath,
                plan,
                reports,
                executionContext.contractView() == null ? null : executionContext.contractView().executionContract(),
                executionContext.language()
        );
        reports = gateOutcome.reports();
        ImplementationStageStatus stageStatus = gateOutcome.stageStatus();
        ImplementationRuntimeSnapshot snapshot = implementationSnapshotAssembler.buildSnapshot(
                plan,
                reports,
                eventJournal.snapshot(),
                note,
                executionContext.language(),
                executionContext.deliveryPolicy(),
                executionContext.sharedContextBundle(),
                taskPackages,
                stageStatus,
                gateOutcome.architectCheckResult(),
                null
        );
        ImplementationExecutionBundle bundle = implementationSnapshotAssembler.buildBundle(snapshot);
        progressSink.publish(bundle);
        return bundle;
    }
}
