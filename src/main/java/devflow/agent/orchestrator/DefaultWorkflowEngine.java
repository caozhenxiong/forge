package devflow.agent.orchestrator;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.artifact.StageArtifactComposer;
import devflow.agent.context.ContextProjector;
import devflow.agent.context.ProjectedContext;
import devflow.agent.loop.AgentLoop;
import devflow.agent.loop.LoopState;
import devflow.agent.loop.LoopStepResult;
import devflow.agent.loop.TransitionDecision;
import devflow.agent.loop.TransitionReason;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.repair.DiagnosisAgent;
import devflow.agent.repair.RepairAgent;
import devflow.agent.repair.RepairBrief;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.StageReviewer;
import devflow.agent.supervisor.SupervisorAction;
import devflow.agent.supervisor.SupervisorAgent;
import devflow.agent.supervisor.SupervisorDecision;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultWorkflowEngine implements WorkflowEngine {

    private final FileRunRepository runRepository;
    private final FileArtifactStore artifactStore;
    private final StageArtifactComposer stageArtifactComposer;
    private final StageReviewer stageReviewer;
    private final EventLogStore eventLogStore;
    private final WorkspaceSnapshotStore snapshotStore;
    private final DiagnosisAgent diagnosisAgent;
    private final RepairAgent repairAgent;
    private final SupervisorAgent supervisorAgent;
    private final ContextProjector contextProjector;
    private final AgentLoop agentLoop;

    public DefaultWorkflowEngine(
            FileRunRepository runRepository,
            FileArtifactStore artifactStore,
            StageArtifactComposer stageArtifactComposer,
            StageReviewer stageReviewer,
            EventLogStore eventLogStore,
            WorkspaceSnapshotStore snapshotStore,
            DiagnosisAgent diagnosisAgent,
            RepairAgent repairAgent,
            SupervisorAgent supervisorAgent,
            ContextProjector contextProjector,
            AgentLoop agentLoop
    ) {
        this.runRepository = runRepository;
        this.artifactStore = artifactStore;
        this.stageArtifactComposer = stageArtifactComposer;
        this.stageReviewer = stageReviewer;
        this.eventLogStore = eventLogStore;
        this.snapshotStore = snapshotStore;
        this.diagnosisAgent = diagnosisAgent;
        this.repairAgent = repairAgent;
        this.supervisorAgent = supervisorAgent;
        this.contextProjector = contextProjector;
        this.agentLoop = agentLoop;
    }

    public void initialize(Path projectPath) {
        runRepository.initialize(projectPath);
    }

    public RunRecord createRun(Path projectPath, String goal, String constraints) {
        runRepository.initialize(projectPath);
        Instant now = Instant.now();
        UUID runId = UUID.randomUUID();
        EnumMap<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        RunRecord runRecord = new RunRecord(
                runId,
                projectPath,
                goal,
                constraints == null ? "" : constraints,
                RunConfig.defaultConfig(),
                StageType.ANALYSIS,
                RunStatus.CREATED,
                stageStates,
                now,
                now
        );
        RunRecord saved = runRepository.save(runRecord);
        snapshotStore.captureBaseline(projectPath, runId);
        eventLogStore.append(projectPath, runId, "Run created.");
        return saved;
    }

    public RunRecord find(Path projectPath, UUID runId) {
        return runRepository.findById(projectPath, runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    }

    @Override
    public RunRecord startRun(UUID runId) {
        throw new UnsupportedOperationException("Use startRun(projectPath, runId)");
    }

    public RunRecord startRun(Path projectPath, UUID runId) {
        RunRecord runRecord = find(projectPath, runId);
        try {
            RunRecord started = enterStage(runRecord, StageType.ANALYSIS, RunStatus.IN_PROGRESS, "初次启动工作流。");
            return progress(projectPath, started);
        } catch (RuntimeException ex) {
            markFatalFailure(projectPath, runId, StageType.ANALYSIS, ex);
            throw ex;
        }
    }

    @Override
    public RunRecord resumeRun(UUID runId) {
        throw new UnsupportedOperationException("Use resumeRun(projectPath, runId)");
    }

    public RunRecord resumeRun(Path projectPath, UUID runId) {
        RunRecord runRecord = find(projectPath, runId);
        if (runRecord.status() == RunStatus.COMPLETED || runRecord.status() == RunStatus.FAILED || runRecord.status() == RunStatus.CANCELLED) {
            return runRecord;
        }

        try {
            StageExecution currentExecution = requireStage(runRecord.stageStates(), runRecord.currentStage());
            if (currentExecution.status() == StageStatus.PENDING || currentExecution.artifactPath() == null) {
                runRecord = enterStage(runRecord, runRecord.currentStage(), RunStatus.IN_PROGRESS, "恢复执行。");
            }
            return progress(projectPath, runRecord);
        } catch (RuntimeException ex) {
            markFatalFailure(projectPath, runId, runRecord.currentStage(), ex);
            throw ex;
        }
    }

    @Override
    public RunRecord approveStage(UUID runId, StageType stageType, String reviewer) {
        throw new UnsupportedOperationException("Use approveStage(projectPath, runId, stageType, reviewer)");
    }

    public RunRecord approveStage(Path projectPath, UUID runId, StageType stageType, String reviewer) {
        RunRecord runRecord = find(projectPath, runId);
        if (runRecord.currentStage() != stageType) {
            throw new IllegalStateException("Stage " + stageType + " is not current stage " + runRecord.currentStage());
        }
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution currentExecution = requireStage(nextStates, stageType);
        if (currentExecution.status() != StageStatus.AWAITING_HUMAN_REVIEW) {
            throw new IllegalStateException("Stage is not awaiting human review: " + stageType);
        }

        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.APPROVED)
                        .withReview(ReviewDecision.APPROVED, "Approved by " + reviewer, "")
        );
        artifactStore.appendReviewHistory(
                projectPath,
                runId,
                stageType,
                renderReviewHistoryEntry(stageType, currentExecution.attempt(), "human:" + reviewer, new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "Approved by " + reviewer, ""))
        );
        eventLogStore.append(projectPath, runId, "Human approved stage " + stageType + " by " + reviewer + ".");

        StageType nextStage = nextStage(stageType);
        Instant now = Instant.now();
        if (nextStage == null) {
            RunRecord completed = runRecord.withCurrentStage(stageType, RunStatus.COMPLETED, nextStates, now);
            return runRepository.save(completed);
        }

        RunRecord draft = runRecord.withCurrentStage(nextStage, RunStatus.IN_PROGRESS, nextStates, now);
        RunRecord entered = enterStage(draft, nextStage, RunStatus.IN_PROGRESS, "由人工批准 " + stageType + " 后进入当前阶段。");
        return progress(projectPath, entered);
    }

    @Override
    public RunRecord rejectStage(UUID runId, StageType stageType, String reviewer, String reason) {
        throw new UnsupportedOperationException("Use rejectStage(projectPath, runId, stageType, reviewer, reason)");
    }

    public RunRecord rejectStage(Path projectPath, UUID runId, StageType stageType, String reviewer, String reason) {
        RunRecord runRecord = find(projectPath, runId);
        if (runRecord.currentStage() != stageType) {
            throw new IllegalStateException("Stage " + stageType + " is not current stage " + runRecord.currentStage());
        }
        StageExecution currentExecution = requireStage(runRecord.stageStates(), stageType);
        artifactStore.appendReviewHistory(
                projectPath,
                runId,
                stageType,
                renderReviewHistoryEntry(
                        stageType,
                        currentExecution.attempt(),
                        "human:" + reviewer,
                        new ReviewResult(ReviewDecision.REJECTED, FixMode.REWORK, "Rejected by " + reviewer, reason)
                )
        );
        RunRecord rerouted = rerouteForRevision(
                projectPath,
                runRecord,
                stageType,
                ReviewDecision.REJECTED,
                FixMode.REWORK,
                "Rejected by " + reviewer,
                reason,
                "",
                "",
                rerouteStage(stageType, FixMode.REWORK),
                false,
                false
        );
        return progress(projectPath, rerouted);
    }

    private RunRecord progress(Path projectPath, RunRecord runRecord) {
        try {
            return agentLoop.runUntilStable(runRecord, loopState -> progressOnce(projectPath, loopState));
        } catch (RuntimeException ex) {
            markFatalFailure(projectPath, runRecord.runId(), runRecord.currentStage(), ex);
            throw ex;
        }
    }

    private LoopStepResult progressOnce(Path projectPath, LoopState loopState) {
        RunRecord current = loopState.runRecord();
        StageType stageType = current.currentStage();
        StageExecution stageExecution = requireStage(current.stageStates(), stageType);
        if (stageExecution.status() != StageStatus.RUNNING) {
            return new LoopStepResult(current, null, false);
        }

        String artifactContent = artifactStore.readArtifact(projectPath, current.runId(), stageType);
        ReviewResult reviewResult = stageReviewer.review(projectPath, current, stageType, artifactContent);
        String reviewArtifact = renderReviewArtifact(stageType, reviewResult);
        artifactStore.writeReviewArtifact(projectPath, current.runId(), stageType, reviewArtifact);
        artifactStore.appendReviewHistory(
                projectPath,
                current.runId(),
                stageType,
                renderReviewHistoryEntry(stageType, stageExecution.attempt(), "agent", reviewResult)
        );
        eventLogStore.append(
                projectPath,
                current.runId(),
                "Agent reviewed " + stageType + " with decision " + reviewResult.decision() + " fixMode=" + reviewResult.fixMode() + "."
        );

        boolean repeatedIssue = reviewResult.decision() != ReviewDecision.APPROVED
                && stageType != StageType.ANALYSIS
                && diagnosisAgent.shouldDiagnose(
                projectPath,
                current,
                stageType,
                reviewResult.fixMode(),
                reviewResult.summary(),
                reviewResult.changeRequest()
        );

        ProjectedContext projectedContext = contextProjector.project(projectPath, current, stageType);
        artifactStore.writeAuxiliaryArtifact(projectPath, current.runId(), "projected_context.md", projectedContext.toMarkdown());
        artifactStore.writeAuxiliaryArtifact(projectPath, current.runId(), "task_memory.md", projectedContext.taskMemory().toMarkdown());

        SupervisorDecision supervisorDecision = supervisorAgent.decide(projectPath, current, stageType, reviewResult, repeatedIssue);
        artifactStore.writeAuxiliaryArtifact(
                projectPath,
                current.runId(),
                "supervisor_decision.md",
                supervisorAgent.renderDecisionArtifact(stageType, reviewResult, repeatedIssue, supervisorDecision)
        );
        TransitionDecision transitionDecision = buildTransitionDecision(stageType, repeatedIssue, reviewResult, supervisorDecision);
        artifactStore.writeAuxiliaryArtifact(
                projectPath,
                current.runId(),
                "transition_decision.md",
                renderTransitionDecision(transitionDecision)
        );
        eventLogStore.append(
                projectPath,
                current.runId(),
                "Supervisor decided action=" + supervisorDecision.action()
                        + " targetStage=" + supervisorDecision.targetStage()
                        + " mode=" + supervisorDecision.mode()
                        + " repeatedIssue=" + repeatedIssue
                        + " transitionReason=" + transitionDecision.reason() + "."
        );

        RunRecord next = applySupervisorDecision(projectPath, current, stageType, reviewResult, repeatedIssue, supervisorDecision);
        boolean continueLoop = next.status() == RunStatus.IN_PROGRESS
                && requireStage(next.stageStates(), next.currentStage()).status() == StageStatus.RUNNING;
        return new LoopStepResult(next, transitionDecision, continueLoop);
    }

    private RunRecord applySupervisorDecision(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            SupervisorDecision supervisorDecision
    ) {
        return switch (supervisorDecision.action()) {
            case ADVANCE_STAGE -> onStageApproved(projectPath, runRecord, stageType, reviewResult, supervisorDecision);
            case REQUEST_HUMAN_REVIEW -> blockForHumanReview(runRecord, stageType, reviewResult);
            case COMPLETE_RUN -> completeRun(runRecord, stageType, reviewResult);
            case RETRY_STAGE, ROLLBACK_STAGE -> rerouteForRevision(
                    projectPath,
                    runRecord,
                    stageType,
                    reviewResult.decision(),
                    supervisorDecision.mode(),
                    reviewResult.summary(),
                    reviewResult.changeRequest(),
                    reviewResult.evidence(),
                    mergeActionItems(reviewResult.actionItems(), supervisorDecision),
                    supervisorDecision.targetStage(),
                    false,
                    repeatedIssue
            );
            case ROUTE_TO_REPAIR -> rerouteForRevision(
                    projectPath,
                    runRecord,
                    stageType,
                    reviewResult.decision(),
                    supervisorDecision.mode(),
                    reviewResult.summary(),
                    reviewResult.changeRequest(),
                    reviewResult.evidence(),
                    mergeActionItems(reviewResult.actionItems(), supervisorDecision),
                    StageType.IMPLEMENTATION,
                    true,
                    repeatedIssue
            );
            case FAIL_RUN -> failRun(runRecord, stageType, reviewResult);
        };
    }

    private RunRecord onStageApproved(Path projectPath, RunRecord runRecord, StageType stageType, ReviewResult reviewResult, SupervisorDecision supervisorDecision) {
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution currentExecution = requireStage(nextStates, stageType);
        Instant now = Instant.now();

        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.APPROVED)
                        .withReview(reviewResult.decision(), reviewResult.summary(), reviewResult.changeRequest())
        );
        StageType nextStage = supervisorDecision.targetStage();
        if (nextStage == null) {
            RunRecord completed = runRecord.withCurrentStage(stageType, RunStatus.COMPLETED, nextStates, now);
            return runRepository.save(completed);
        }

        RunRecord draft = runRecord.withCurrentStage(nextStage, RunStatus.IN_PROGRESS, nextStates, now);
        return enterStage(
                draft,
                nextStage,
                RunStatus.IN_PROGRESS,
                "由 " + stageType + " 阶段自动批准后进入当前阶段。\n\n" + renderSupervisorGuidance(supervisorDecision)
        );
    }

    private RunRecord blockForHumanReview(RunRecord runRecord, StageType stageType, ReviewResult reviewResult) {
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution currentExecution = requireStage(nextStates, stageType);
        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.AWAITING_HUMAN_REVIEW)
                        .withReview(reviewResult.decision(), reviewResult.summary(), reviewResult.changeRequest())
        );
        RunRecord blocked = runRecord.withCurrentStage(stageType, RunStatus.BLOCKED, nextStates, Instant.now());
        return runRepository.save(blocked);
    }

    private RunRecord completeRun(RunRecord runRecord, StageType stageType, ReviewResult reviewResult) {
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution currentExecution = requireStage(nextStates, stageType);
        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.APPROVED)
                        .withReview(reviewResult.decision(), reviewResult.summary(), reviewResult.changeRequest())
        );
        return runRepository.save(runRecord.withCurrentStage(stageType, RunStatus.COMPLETED, nextStates, Instant.now()));
    }

    private RunRecord failRun(RunRecord runRecord, StageType stageType, ReviewResult reviewResult) {
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution currentExecution = requireStage(nextStates, stageType);
        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.FAILED)
                        .withReview(reviewResult.decision(), reviewResult.summary(), reviewResult.changeRequest())
        );
        return runRepository.save(runRecord.withCurrentStage(stageType, RunStatus.FAILED, nextStates, Instant.now()));
    }

    private RunRecord rerouteForRevision(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            ReviewDecision decision,
            FixMode fixMode,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            StageType rerouteStage,
            boolean forceRepair,
            boolean repeatedIssue
    ) {
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution currentExecution = requireStage(nextStates, stageType);
        StageExecution reviewedExecution = currentExecution.withStatus(StageStatus.NEEDS_REVISION)
                .withReview(decision, summary, changeRequest);
        nextStates.put(stageType, reviewedExecution);

        // Auto-revision is intentionally bounded so a bad prompt/model output cannot loop forever.
        if (currentExecution.attempt() >= runRecord.config().maxAutoRevisions()) {
            nextStates.put(stageType, reviewedExecution.withStatus(StageStatus.FAILED));
            RunRecord failed = runRecord.withCurrentStage(stageType, RunStatus.FAILED, nextStates, Instant.now());
            eventLogStore.append(projectPath, runRecord.runId(), "Stage " + stageType + " exceeded max auto revisions.");
            return runRepository.save(failed);
        }

        String revisionNote = buildRevisionNote(fixMode, summary, changeRequest, evidence, actionItems);
        if (rerouteStage == StageType.IMPLEMENTATION && (forceRepair || repeatedIssue)) {
            RepairBrief repairBrief = diagnosisAgent.diagnose(
                    projectPath,
                    runRecord,
                    stageType,
                    fixMode,
                    summary,
                    changeRequest
            );
            artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), "repair_brief.md", repairBrief.toMarkdown());
            revisionNote = repairAgent.buildRepairNote(fixMode, summary, changeRequest, evidence, actionItems, repairBrief);
            eventLogStore.append(
                    projectPath,
                    runRecord.runId(),
                    "Diagnosis triggered for stage " + stageType + " and produced repair_brief.md with mode=" + repairBrief.recommendedMode() + "."
            );
        }
        RunRecord draft = runRecord.withCurrentStage(rerouteStage, RunStatus.IN_PROGRESS, nextStates, Instant.now());
        eventLogStore.append(
                projectPath,
                runRecord.runId(),
                "Stage " + stageType + " routed back to " + rerouteStage + " due to review with fixMode=" + fixMode + "."
        );
        return enterStage(draft, rerouteStage, RunStatus.IN_PROGRESS, revisionNote);
    }

    private RunRecord enterStage(RunRecord runRecord, StageType stageType, RunStatus runStatus, String note) {
        Map<StageType, StageExecution> nextStates = new EnumMap<>(runRecord.stageStates());
        StageExecution nextExecution = requireStage(nextStates, stageType).nextAttempt(StageStatus.RUNNING);
        nextStates.put(
                stageType,
                nextExecution.withArtifactPath(null)
                        .withReview(null, null, null)
        );
        RunRecord draft = runRecord.withCurrentStage(stageType, runStatus, nextStates, Instant.now());
        RunRecord persistedRunning = runRepository.save(draft);
        eventLogStore.append(runRecord.projectPath(), runRecord.runId(), "Entering stage " + stageType + " attempt=" + nextExecution.attempt() + ".");

        try {
            // StageArtifactComposer encapsulates the stage-specific generation/execution logic.
            String artifactContent = stageArtifactComposer.compose(runRecord.projectPath(), persistedRunning, stageType, note);
            String artifactPath = artifactStore.writeArtifact(runRecord.projectPath(), runRecord.runId(), stageType, artifactContent).toString();
            nextStates.put(
                    stageType,
                    nextExecution.withArtifactPath(artifactPath)
                            .withReview(null, null, null)
            );
            eventLogStore.append(runRecord.projectPath(), runRecord.runId(), "Entered stage " + stageType + " attempt=" + nextExecution.attempt() + ".");
            RunRecord saved = persistedRunning.withCurrentStage(stageType, runStatus, nextStates, Instant.now());
            return runRepository.save(saved);
        } catch (RuntimeException ex) {
            markFatalFailure(runRecord.projectPath(), runRecord.runId(), stageType, ex);
            throw ex;
        }
    }

    private void markFatalFailure(Path projectPath, UUID runId, StageType stageType, RuntimeException ex) {
        RunRecord latest = runRepository.findById(projectPath, runId).orElse(null);
        if (latest == null || latest.status() == RunStatus.FAILED || latest.status() == RunStatus.COMPLETED || latest.status() == RunStatus.CANCELLED) {
            return;
        }
        Map<StageType, StageExecution> nextStates = new EnumMap<>(latest.stageStates());
        StageExecution currentExecution = requireStage(nextStates, stageType);
        nextStates.put(
                stageType,
                currentExecution.withStatus(StageStatus.FAILED)
                        .withReview(
                                ReviewDecision.REJECTED,
                                "Stage failed due to runtime exception",
                                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                        )
        );
        eventLogStore.append(projectPath, runId, "Stage " + stageType + " failed due to fatal error: " + ex.getMessage());
        runRepository.save(latest.withCurrentStage(stageType, RunStatus.FAILED, nextStates, Instant.now()));
    }

    private String renderReviewArtifact(StageType stageType, ReviewResult reviewResult) {
        return """
                # %s 阶段审阅结果

                - decision: %s
                - fixMode: %s
                - summary: %s
                - changeRequest: %s
                - evidence: %s
                - actionItems: %s
                """.formatted(
                stageType,
                reviewResult.decision(),
                reviewResult.fixMode(),
                nullToEmpty(reviewResult.summary()),
                nullToEmpty(reviewResult.changeRequest()),
                nullToEmpty(reviewResult.evidence()),
                nullToEmpty(reviewResult.actionItems())
        );
    }

    private String renderReviewHistoryEntry(StageType stageType, int attempt, String reviewer, ReviewResult reviewResult) {
        return """
                ## attempt=%d reviewer=%s stage=%s

                - decision: %s
                - fixMode: %s
                - summary: %s
                - changeRequest: %s
                - evidence: %s
                - actionItems: %s

                """.formatted(
                attempt,
                reviewer,
                stageType,
                reviewResult.decision(),
                reviewResult.fixMode(),
                nullToEmpty(reviewResult.summary()),
                nullToEmpty(reviewResult.changeRequest()),
                nullToEmpty(reviewResult.evidence()),
                nullToEmpty(reviewResult.actionItems())
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private StageExecution requireStage(Map<StageType, StageExecution> stageStates, StageType stageType) {
        StageExecution stageExecution = stageStates.get(stageType);
        if (stageExecution == null) {
            throw new IllegalArgumentException("Missing stage state for " + stageType);
        }
        return stageExecution;
    }

    private StageType nextStage(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> StageType.PRD;
            case PRD -> StageType.DESIGN;
            case DESIGN -> StageType.IMPLEMENTATION;
            case IMPLEMENTATION -> StageType.CODE_REVIEW;
            case CODE_REVIEW -> StageType.TEST;
            case TEST -> null;
        };
    }

    private StageType rerouteStage(StageType stageType, FixMode fixMode) {
        return switch (stageType) {
            case ANALYSIS, PRD, DESIGN -> stageType;
            case IMPLEMENTATION, CODE_REVIEW, TEST -> StageType.IMPLEMENTATION;
        };
    }

    private String buildRevisionNote(FixMode fixMode, String summary, String changeRequest, String evidence, String actionItems) {
        return """
                [FIX_MODE=%s]
                摘要：
                %s

                修改要求：
                %s

                关键证据：
                %s

                建议动作：
                %s
                """.formatted(
                fixMode == null ? FixMode.PATCH : fixMode,
                nullToEmpty(summary),
                nullToEmpty(changeRequest),
                nullToEmpty(evidence),
                nullToEmpty(actionItems)
        ).trim();
    }

    private String mergeActionItems(String actionItems, SupervisorDecision supervisorDecision) {
        StringBuilder builder = new StringBuilder(nullToEmpty(actionItems).trim());
        String supervisorGuidance = renderSupervisorGuidance(supervisorDecision).trim();
        if (!supervisorGuidance.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(supervisorGuidance);
        }
        return builder.toString().trim();
    }

    private String renderSupervisorGuidance(SupervisorDecision supervisorDecision) {
        if (supervisorDecision == null) {
            return "";
        }
        String focus = renderList(supervisorDecision.focus());
        String constraints = renderList(supervisorDecision.constraints());
        String requiredEvidence = renderList(supervisorDecision.requiredEvidence());
        if (focus.isBlank()
                && constraints.isBlank()
                && requiredEvidence.isBlank()
                && nullToEmpty(supervisorDecision.reason()).isBlank()) {
            return "";
        }
        return """
                [SUPERVISOR_GUIDANCE]
                [DELIVERY_MODE=%s]
                [DELIVERY_MAX_FILES=%d]
                [DELIVERY_MAX_SYMBOLS=%d]
                [DELIVERY_PREFER_PRECISE_EDITING=%s]
                [DELIVERY_FORCE_BACKLOG_SPLIT=%s]
                [DELIVERY_REQUIRE_VERIFICATION=%s]
                决策原因：
                %s

                本轮焦点：
                %s

                本轮约束：
                %s

                本轮必需证据：
                %s
                """.formatted(
                supervisorDecision.deliveryPolicy().mode(),
                supervisorDecision.deliveryPolicy().maxFiles(),
                supervisorDecision.deliveryPolicy().maxSymbols(),
                supervisorDecision.deliveryPolicy().preferPreciseEditing(),
                supervisorDecision.deliveryPolicy().forceBacklogSplit(),
                supervisorDecision.deliveryPolicy().requireVerificationBeforeReview(),
                nullToEmpty(supervisorDecision.reason()),
                focus.isBlank() ? "- 无" : focus,
                constraints.isBlank() ? "- 无" : constraints,
                requiredEvidence.isBlank() ? "- 无" : requiredEvidence
        ).trim();
    }

    private TransitionDecision buildTransitionDecision(
            StageType stageType,
            boolean repeatedIssue,
            ReviewResult reviewResult,
            SupervisorDecision supervisorDecision
    ) {
        TransitionReason reason = switch (supervisorDecision.action()) {
            case ADVANCE_STAGE -> StageType.TEST == stageType ? TransitionReason.RUN_COMPLETED : TransitionReason.STAGE_APPROVED;
            case REQUEST_HUMAN_REVIEW -> TransitionReason.HUMAN_REVIEW_REQUIRED;
            case RETRY_STAGE -> TransitionReason.STAGE_RETRY;
            case ROUTE_TO_REPAIR -> TransitionReason.REPAIR_ROUTE;
            case ROLLBACK_STAGE -> TransitionReason.STAGE_ROLLBACK;
            case COMPLETE_RUN -> TransitionReason.RUN_COMPLETED;
            case FAIL_RUN -> TransitionReason.RUN_FAILED;
        };
        return new TransitionDecision(
                reason,
                stageType,
                supervisorDecision.targetStage(),
                repeatedIssue,
                nullToEmpty(reviewResult.summary()),
                supervisorDecision
        );
    }

    private String renderTransitionDecision(TransitionDecision decision) {
        return """
                # Transition Decision

                - reason: %s
                - fromStage: %s
                - targetStage: %s
                - repeatedIssue: %s
                - reviewSummary: %s
                - supervisorAction: %s
                - supervisorMode: %s

                ## Delivery Policy

                - mode: %s
                - maxFiles: %s
                - maxSymbols: %s
                - preferPreciseEditing: %s
                - forceBacklogSplit: %s
                - requireVerificationBeforeReview: %s

                ## Focus

                %s

                ## Constraints

                %s

                ## Required Evidence

                %s
                """.formatted(
                decision.reason(),
                decision.fromStage(),
                decision.targetStage() == null ? "null" : decision.targetStage(),
                decision.repeatedIssue(),
                nullToEmpty(decision.summary()),
                decision.supervisorDecision().action(),
                decision.supervisorDecision().mode(),
                decision.supervisorDecision().deliveryPolicy().mode(),
                decision.supervisorDecision().deliveryPolicy().maxFiles(),
                decision.supervisorDecision().deliveryPolicy().maxSymbols(),
                decision.supervisorDecision().deliveryPolicy().preferPreciseEditing(),
                decision.supervisorDecision().deliveryPolicy().forceBacklogSplit(),
                decision.supervisorDecision().deliveryPolicy().requireVerificationBeforeReview(),
                renderList(decision.supervisorDecision().focus()),
                renderList(decision.supervisorDecision().constraints()),
                renderList(decision.supervisorDecision().requiredEvidence())
        );
    }

    private String renderList(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ").append(value.trim());
        }
        return builder.toString();
    }
}
