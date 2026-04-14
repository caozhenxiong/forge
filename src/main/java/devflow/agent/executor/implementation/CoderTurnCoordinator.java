package devflow.agent.executor.implementation;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.context.ContractView;
import devflow.agent.domain.RunRecord;
import java.nio.file.Path;
import java.util.List;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.implementation.ImplementationEventMessages;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
import devflow.agent.executor.subtask.TaskPackage;
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
public final class CoderTurnCoordinator {

    private final EventLogStore eventLogStore;
    private final FileArtifactStore fileArtifactStore;
    private final ImplementationStageGate implementationStageGate;
    private final ImplementationGateEngine implementationGateEngine;
    private final ImplementationResumePolicy implementationResumePolicy;
    private final ImplementationPlanner implementationPlanner;
    private final ImplementationPlanRunner implementationPlanRunner;
    private final ImplementationSnapshotAssembler implementationSnapshotAssembler;

    public CoderTurnCoordinator(
            EventLogStore eventLogStore,
            FileArtifactStore fileArtifactStore,
            ImplementationStageGate implementationStageGate,
            ImplementationGateEngine implementationGateEngine,
            ImplementationResumePolicy implementationResumePolicy,
            ImplementationPlanner implementationPlanner,
            ImplementationPlanRunner implementationPlanRunner,
            ImplementationSnapshotAssembler implementationSnapshotAssembler
    ) {
        this.eventLogStore = eventLogStore;
        this.fileArtifactStore = fileArtifactStore;
        this.implementationStageGate = implementationStageGate;
        this.implementationGateEngine = implementationGateEngine;
        this.implementationResumePolicy = implementationResumePolicy;
        this.implementationPlanner = implementationPlanner;
        this.implementationPlanRunner = implementationPlanRunner;
        this.implementationSnapshotAssembler = implementationSnapshotAssembler;
    }

    public ImplementationExecutionBundle execute(
            Path projectPath,
            RunRecord runRecord,
            String note,
            ImplementationExecutionContext executionContext,
            ImplementationProgressSink progressSink
    ) {
        ImplementationEventJournal eventJournal = new ImplementationEventJournal(
                eventLogStore,
                fileArtifactStore,
                projectPath,
                runRecord
        );
        ReusableImplementationState reusableState = implementationResumePolicy.loadReusableImplementationState(
                executionContext.previousStateJson(),
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
        ImplementationPlan plan;
        try {
            plan = reusableState == null
                    ? implementationPlanner.plan(new PlanningRequest(
                    runRecord,
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
                    null,
                    eventJournal
            ))
                    : reusableState.plan();
        } catch (ImplementationPlanningException exception) {
            if (!exception.reason().recoverable()) {
                throw exception;
            }
            throw new IllegalStateException(
                    "Implementation planning exhausted internal retries: " + blankIfNull(exception.getMessage()),
                    exception
            );
        }
        List<TaskPackage> taskPackages = implementationSnapshotAssembler.buildTaskPackages(
                projectPath,
                plan,
                executionContext.sharedContextBundle(),
                executionContext.contractView(),
                executionContext.fingerprint()
        );
        devflow.agent.context.ExecutionContract executionContract =
                executionContext.contractView() == null ? null : executionContext.contractView().executionContract();
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
                        plan.subtasks().stream().map(Subtask::title).toList()
                ),
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
                        implementationStageGate.summarizeStageStatus(
                                plan,
                                currentReports,
                                implementationGateEngine.currentContractGate(
                                        projectPath,
                                        plan,
                                        currentReports,
                                        executionContract
                                )
                        ),
                        currentSubtaskTitle
                ),
                eventJournal
        );
        ImplementationGateOutcome gateOutcome = implementationGateEngine.evaluate(
                projectPath,
                plan,
                reports,
                executionContract,
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
                null
        );
        ImplementationExecutionBundle bundle = implementationSnapshotAssembler.buildBundle(snapshot);
        progressSink.publish(bundle);
        return bundle;
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
