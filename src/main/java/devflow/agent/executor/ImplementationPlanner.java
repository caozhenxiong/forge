package devflow.agent.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * implementation planning 的唯一 owner。
 *
 * <p>最终主链固定为：
 * 1. 先规划并验收 outline；
 * 2. 再逐个规划并验收 subtask detail；
 * 3. 本地 assemble 最终 {@link ImplementationPlan}；
 * 4. 最后再做整体验收 gate。
 *
 * <p>这样可以把 planning 从“大 JSON 一次生成”收敛为“小协议 + 本地组装 + 单元级重试”。
 */
class ImplementationPlanner {

    private final ObjectMapper objectMapper;
    private final ImplementationPlanGate implementationPlanGate;
    private final ImplementationOutlineGate outlineGate;
    private final ImplementationSubtaskDetailGate detailGate;
    private final ImplementationPlanAssembler planAssembler;
    private final ImplementationPlanningFeedbackRouter feedbackRouter;
    private final ImplementationPlanGateInputBuilder gateInputBuilder;
    private final int maxPlanningUnitAttempts;
    private final ImplementationPlanningPromptAssembler promptAssembler;
    private final ImplementationPlanningTurnRunner planningTurnRunner;
    private final ImplementationPlanningPayloadParser payloadParser;

    ImplementationPlanner(
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            ImplementationPlanCoverageAnalyzer coverageAnalyzer,
            devflow.agent.loop.AgentTurnLoop agentTurnLoop,
            int payloadRepairAttempts,
            int maxPlanningUnitAttempts,
            int maxFilesPerSubtask,
            int maxDeliveryPolicyFiles
    ) {
        this.objectMapper = objectMapper;
        this.implementationPlanGate = new ImplementationPlanGate(coverageAnalyzer);
        this.outlineGate = new ImplementationOutlineGate(coverageAnalyzer);
        this.detailGate = new ImplementationSubtaskDetailGate(new ImplementationPlanChangeGate());
        this.planAssembler = new ImplementationPlanAssembler();
        this.feedbackRouter = new ImplementationPlanningFeedbackRouter();
        this.gateInputBuilder = new ImplementationPlanGateInputBuilder();
        this.maxPlanningUnitAttempts = maxPlanningUnitAttempts;
        this.promptAssembler = new ImplementationPlanningPromptAssembler(maxFilesPerSubtask, maxDeliveryPolicyFiles);
        this.planningTurnRunner = new ImplementationPlanningTurnRunner(llmProvider, agentTurnLoop);
        this.payloadParser = new ImplementationPlanningPayloadParser(llmProvider, objectMapper, payloadRepairAttempts);
    }

    ImplementationPlan plan(
            Path projectPath,
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            String workspaceContext,
            String plannerContextMarkdown,
            String performanceValidationGuidance,
            boolean preferSkeletonFlow,
            DeliveryPolicyEnvelope deliveryPolicy,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            DocumentLanguage language,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            String requirementCatalog,
            ImplementationContinuationConstraints continuationConstraints,
            ImplementationEventJournal eventJournal
    ) {
        PlanningAttemptLedger attemptLedger = new PlanningAttemptLedger();
        String outlineFeedback = "";
        Map<String, String> detailFeedback = new LinkedHashMap<>();
        Map<String, ImplementationSubtaskDetail> acceptedDetails = new LinkedHashMap<>();
        ImplementationOutline outline = null;
        while (true) {
            if (outline == null) {
                outline = planOutline(
                        runRecord,
                        note,
                        workspaceContext,
                        plannerContextMarkdown,
                        deliveryPolicy,
                        contractView,
                        qualityPlan,
                        fingerprint,
                        language,
                        fixMode,
                        implementationPatchTarget,
                        requirementCatalog,
                        continuationConstraints,
                        eventJournal,
                        attemptLedger,
                        outlineFeedback
                );
                acceptedDetails.clear();
                detailFeedback.clear();
            }
            for (ImplementationOutlineSubtask subtask : outline.subtasks()) {
                if (subtask == null || acceptedDetails.containsKey(subtask.id())) {
                    continue;
                }
                acceptedDetails.put(subtask.id(), planSubtaskDetail(
                        runRecord,
                        workspaceContext,
                        deliveryPolicy,
                        contractView,
                        qualityPlan,
                        fingerprint,
                        language,
                        fixMode,
                        implementationPatchTarget,
                        continuationConstraints,
                        outline,
                        subtask,
                        eventJournal,
                        attemptLedger,
                        detailFeedback.getOrDefault(subtask.id(), "")
                ));
            }
            ImplementationPlan finalPlan = planAssembler.assemble(outline, acceptedDetails);
            GateReport gateReport = implementationPlanGate.evaluate(
                    gateInputBuilder.build(
                            fingerprint,
                            contractView,
                            qualityPlan,
                            implementationPatchTarget,
                            continuationConstraints,
                            finalPlan
                    )
            );
            if (gateReport.passed()) {
                return finalPlan;
            }
            ImplementationPlanningFeedbackRouter.RouteDecision routeDecision = feedbackRouter.route(gateReport, outline);
            if (routeDecision == null) {
                throw new ImplementationPlanningException(
                        ImplementationPlanningFailureReason.COVERAGE_MISMATCH,
                        "Implementation plan does not satisfy execution contract: " + implementationPlanGate.toPlanningFeedback(gateReport)
                );
            }
            eventJournal.append(ImplementationEventMessages.planningFinalGateReroute(
                    routeDecision.unitKind(),
                    routeDecision.unitId(),
                    routeDecision.feedback()
            ));
            if (routeDecision.unitKind() == ImplementationPlanningUnitKind.OUTLINE) {
                outlineFeedback = mergeFeedback(outlineFeedback, implementationPlanGate.toPlanningFeedback(gateReport));
                outline = null;
                acceptedDetails.clear();
                detailFeedback.clear();
                continue;
            }
            detailFeedback.put(
                    routeDecision.unitId(),
                    mergeFeedback(detailFeedback.getOrDefault(routeDecision.unitId(), ""), routeDecision.feedback())
            );
            acceptedDetails.remove(routeDecision.unitId());
        }
    }

    private ImplementationOutline planOutline(
            RunRecord runRecord,
            String note,
            String workspaceContext,
            String plannerContextMarkdown,
            DeliveryPolicyEnvelope deliveryPolicy,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            DocumentLanguage language,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            String requirementCatalog,
            ImplementationContinuationConstraints continuationConstraints,
            ImplementationEventJournal eventJournal,
            PlanningAttemptLedger attemptLedger,
            String initialFeedback
    ) {
        String feedback = initialFeedback;
        ImplementationPlanningException lastFailure = null;
        while (true) {
            int attempt = attemptLedger.nextAttempt(ImplementationPlanningUnitKind.OUTLINE, "outline");
            if (attempt > maxPlanningUnitAttempts) {
                throw exhaustedPlanningUnit(ImplementationPlanningUnitKind.OUTLINE, "outline", lastFailure);
            }
            eventJournal.append(ImplementationEventMessages.planningUnitStarted(
                    ImplementationPlanningUnitKind.OUTLINE,
                    "outline",
                    attempt,
                    maxPlanningUnitAttempts
            ));
            ImplementationPlanningPrompt prompt = promptAssembler.assembleOutline(
                    runRecord,
                    note,
                    workspaceContext,
                    plannerContextMarkdown,
                    deliveryPolicy,
                    contractView,
                    qualityPlan,
                    language,
                    fixMode,
                    implementationPatchTarget,
                    requirementCatalog,
                    continuationConstraints,
                    feedback
            );
            ImplementationPlanningTurnRunner.PlanningTurnOutcome outcome = planningTurnRunner.run(
                    "implementation-outline#" + attempt,
                    prompt.systemPrompt(),
                    prompt.userPrompt()
            );
            try {
                eventJournal.writeAttemptScopedPlanningArtifact(
                        ImplementationPlanningArtifactNames.rawResponse(ImplementationPlanningUnitKind.OUTLINE, "outline"),
                        attempt,
                        outcome.response()
                );
                ImplementationPlanningPayloadParser.ParseResult<ImplementationOutline> parseResult =
                        payloadParser.parseOutline(outcome.response(), eventJournal, attempt);
                if (parseResult.repairedResponse() != null && !parseResult.repairedResponse().isBlank()) {
                    eventJournal.append(ImplementationEventMessages.planningUnitRepairApplied(
                            ImplementationPlanningUnitKind.OUTLINE,
                            "outline"
                    ));
                }
                GateReport gateReport = outlineGate.evaluate(
                        fingerprint,
                        contractView,
                        qualityPlan,
                        deliveryPolicy,
                        parseResult.payload()
                );
                if (gateReport.passed()) {
                    eventJournal.writePlanningArtifact(
                            ImplementationPlanningArtifactNames.acceptedOutline(),
                            toPrettyJson(parseResult.payload())
                    );
                    eventJournal.append(ImplementationEventMessages.planningUnitAccepted(
                            ImplementationPlanningUnitKind.OUTLINE,
                            "outline",
                            attempt,
                            maxPlanningUnitAttempts,
                            outcome.telemetry()
                    ));
                    return parseResult.payload();
                }
                String nextFeedback = outlineGate.toRetryFeedback(gateReport);
                eventJournal.append(ImplementationEventMessages.planningUnitRejected(
                        ImplementationPlanningUnitKind.OUTLINE,
                        "outline",
                        attempt,
                        maxPlanningUnitAttempts,
                        nextFeedback,
                        outcome.telemetry()
                ));
                lastFailure = new ImplementationPlanningException(
                        ImplementationPlanningFailureReason.COVERAGE_MISMATCH,
                        nextFeedback,
                        null,
                        outcome.telemetry()
                );
                feedback = mergeFeedback(feedback, nextFeedback);
            } catch (ImplementationPlanningException exception) {
                if (!exception.reason().recoverable()) {
                    throw exception;
                }
                eventJournal.append(ImplementationEventMessages.planningUnitRejected(
                        ImplementationPlanningUnitKind.OUTLINE,
                        "outline",
                        attempt,
                        maxPlanningUnitAttempts,
                        exception.getMessage(),
                        outcome.telemetry()
                ));
                lastFailure = exception;
                feedback = mergeFeedback(feedback, exception.getMessage());
            }
        }
    }

    private ImplementationSubtaskDetail planSubtaskDetail(
            RunRecord runRecord,
            String workspaceContext,
            DeliveryPolicyEnvelope deliveryPolicy,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            DocumentLanguage language,
            FixMode fixMode,
            ImplementationPatchTarget implementationPatchTarget,
            ImplementationContinuationConstraints continuationConstraints,
            ImplementationOutline outline,
            ImplementationOutlineSubtask subtask,
            ImplementationEventJournal eventJournal,
            PlanningAttemptLedger attemptLedger,
            String initialFeedback
    ) {
        String feedback = initialFeedback;
        ImplementationPlanningException lastFailure = null;
        while (true) {
            int attempt = attemptLedger.nextAttempt(ImplementationPlanningUnitKind.SUBTASK_DETAIL, subtask.id());
            if (attempt > maxPlanningUnitAttempts) {
                throw exhaustedPlanningUnit(ImplementationPlanningUnitKind.SUBTASK_DETAIL, subtask.id(), lastFailure);
            }
            eventJournal.append(ImplementationEventMessages.planningUnitStarted(
                    ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                    subtask.id(),
                    attempt,
                    maxPlanningUnitAttempts
            ));
            ImplementationPlanningPrompt prompt = promptAssembler.assembleSubtaskDetail(
                    runRecord,
                    workspaceContext,
                    contractView,
                    qualityPlan,
                    language,
                    deliveryPolicy,
                    fixMode,
                    implementationPatchTarget,
                    continuationConstraints,
                    outline,
                    subtask,
                    feedback
            );
            ImplementationPlanningTurnRunner.PlanningTurnOutcome outcome = planningTurnRunner.run(
                    "implementation-subtask-detail#" + subtask.id() + "#" + attempt,
                    prompt.systemPrompt(),
                    prompt.userPrompt()
            );
            try {
                eventJournal.writeAttemptScopedPlanningArtifact(
                        ImplementationPlanningArtifactNames.rawResponse(ImplementationPlanningUnitKind.SUBTASK_DETAIL, subtask.id()),
                        attempt,
                        outcome.response()
                );
                ImplementationPlanningPayloadParser.ParseResult<ImplementationSubtaskDetail> parseResult =
                        payloadParser.parseSubtaskDetail(subtask.id(), outcome.response(), eventJournal, attempt);
                if (parseResult.repairedResponse() != null && !parseResult.repairedResponse().isBlank()) {
                    eventJournal.append(ImplementationEventMessages.planningUnitRepairApplied(
                            ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                            subtask.id()
                    ));
                }
                GateReport gateReport = detailGate.evaluate(
                        fingerprint,
                        contractView,
                        deliveryPolicy,
                        continuationConstraints,
                        subtask,
                        parseResult.payload()
                );
                if (gateReport.passed()) {
                    eventJournal.writePlanningArtifact(
                            ImplementationPlanningArtifactNames.acceptedSubtaskDetail(subtask.id()),
                            toPrettyJson(parseResult.payload())
                    );
                    eventJournal.append(ImplementationEventMessages.planningUnitAccepted(
                            ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                            subtask.id(),
                            attempt,
                            maxPlanningUnitAttempts,
                            outcome.telemetry()
                    ));
                    return parseResult.payload();
                }
                String nextFeedback = detailGate.toRetryFeedback(gateReport);
                eventJournal.append(ImplementationEventMessages.planningUnitRejected(
                        ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                        subtask.id(),
                        attempt,
                        maxPlanningUnitAttempts,
                        nextFeedback,
                        outcome.telemetry()
                ));
                lastFailure = new ImplementationPlanningException(
                        ImplementationPlanningFailureReason.COVERAGE_MISMATCH,
                        nextFeedback,
                        null,
                        outcome.telemetry()
                );
                feedback = mergeFeedback(feedback, nextFeedback);
            } catch (ImplementationPlanningException exception) {
                if (!exception.reason().recoverable()) {
                    throw exception;
                }
                eventJournal.append(ImplementationEventMessages.planningUnitRejected(
                        ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                        subtask.id(),
                        attempt,
                        maxPlanningUnitAttempts,
                        exception.getMessage(),
                        outcome.telemetry()
                ));
                lastFailure = exception;
                feedback = mergeFeedback(feedback, exception.getMessage());
            }
        }
    }

    private String mergeFeedback(String inheritedFeedback, String newFeedback) {
        String inherited = blankIfNull(inheritedFeedback).trim();
        String fresh = blankIfNull(newFeedback).trim();
        if (inherited.isBlank()) {
            return fresh;
        }
        if (fresh.isBlank()) {
            return inherited;
        }
        return inherited + "\n\n" + fresh;
    }

    private String toPrettyJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize implementation planning artifact", exception);
        }
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private ImplementationPlanningException exhaustedPlanningUnit(
            ImplementationPlanningUnitKind unitKind,
            String unitId,
            ImplementationPlanningException lastFailure
    ) {
        if (lastFailure == null) {
            return new ImplementationPlanningException(
                    ImplementationPlanningFailureReason.PLAN_GENERATION_EXHAUSTED,
                    "Implementation planning exhausted attempts for %s (%s)".formatted(unitKind, unitId)
            );
        }
        return new ImplementationPlanningException(
                lastFailure.reason(),
                "Implementation planning exhausted attempts for %s (%s): %s"
                        .formatted(unitKind, unitId, blankIfNull(lastFailure.getMessage()).trim()),
                lastFailure,
                lastFailure.telemetry()
        );
    }

    private static final class PlanningAttemptLedger {
        private final Map<String, Integer> attempts = new LinkedHashMap<>();

        private PlanningAttemptLedger() {
        }

        private int nextAttempt(ImplementationPlanningUnitKind unitKind, String unitId) {
            String key = unitKind.name() + ":" + unitId;
            int nextAttempt = attempts.getOrDefault(key, 0) + 1;
            attempts.put(key, nextAttempt);
            return nextAttempt;
        }
    }
}
