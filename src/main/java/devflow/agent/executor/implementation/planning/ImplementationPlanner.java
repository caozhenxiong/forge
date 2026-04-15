package devflow.agent.executor.implementation.planning;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.implementation.ImplementationEventMessages;
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
public class ImplementationPlanner {

    private final ObjectMapper objectMapper;
    private final ImplementationPlanGate implementationPlanGate;
    private final ImplementationOutlineGate outlineGate;
    private final ImplementationSubtaskDetailGate detailGate;
    private final ImplementationPlanAssembler planAssembler;
    private final ImplementationPlanningFeedbackRouter feedbackRouter;
    private final ImplementationPlanGateInputBuilder gateInputBuilder;
    private final PlanningRuntimeFactsResolver planningRuntimeFactsResolver;
    private final int maxPlanningUnitAttempts;
    private final ImplementationPlanningPromptAssembler promptAssembler;
    private final ImplementationPlanningTurnRunner planningTurnRunner;
    private final ImplementationPlanningPayloadParser payloadParser;

    public ImplementationPlanner(
            ObjectMapper objectMapper,
            ImplementationPlanGate implementationPlanGate,
            ImplementationOutlineGate outlineGate,
            ImplementationSubtaskDetailGate detailGate,
            ImplementationPlanAssembler planAssembler,
            ImplementationPlanningFeedbackRouter feedbackRouter,
            ImplementationPlanGateInputBuilder gateInputBuilder,
            PlanningRuntimeFactsResolver planningRuntimeFactsResolver,
            int maxPlanningUnitAttempts,
            ImplementationPlanningPromptAssembler promptAssembler,
            ImplementationPlanningTurnRunner planningTurnRunner,
            ImplementationPlanningPayloadParser payloadParser
    ) {
        this.objectMapper = objectMapper;
        this.implementationPlanGate = implementationPlanGate;
        this.outlineGate = outlineGate;
        this.detailGate = detailGate;
        this.planAssembler = planAssembler;
        this.feedbackRouter = feedbackRouter;
        this.gateInputBuilder = gateInputBuilder;
        this.planningRuntimeFactsResolver = planningRuntimeFactsResolver;
        this.maxPlanningUnitAttempts = maxPlanningUnitAttempts;
        this.promptAssembler = promptAssembler;
        this.planningTurnRunner = planningTurnRunner;
        this.payloadParser = payloadParser;
    }

    public ImplementationPlan plan(PlanningRequest request) {
        PlanningAttemptLedger attemptLedger = new PlanningAttemptLedger();
        PlanningRuntimeFacts runtimeFacts = request.planningRuntimeFacts() == null
                ? planningRuntimeFactsResolver.resolve(request)
                : request.planningRuntimeFacts();
        String outlineFeedback = "";
        Map<String, String> detailFeedback = new LinkedHashMap<>();
        Map<String, ImplementationSubtaskDetail> acceptedDetails = new LinkedHashMap<>();
        ImplementationOutline outline = null;
        while (true) {
            if (outline == null) {
                outline = planOutline(
                        request.runRecord(),
                        request.note(),
                        request.workspaceContext(),
                        request.plannerContextMarkdown(),
                        request.deliveryPolicy(),
                        runtimeFacts,
                        request.contractView(),
                        request.qualityPlan(),
                        request.fingerprint(),
                        request.language(),
                        request.fixMode(),
                        request.implementationPatchTarget(),
                        request.requirementCatalog(),
                        request.continuationConstraints(),
                        request.eventJournal(),
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
                        request.runRecord(),
                        request.workspaceContext(),
                        request.deliveryPolicy(),
                        runtimeFacts,
                        request.contractView(),
                        request.qualityPlan(),
                        request.fingerprint(),
                        request.language(),
                        request.fixMode(),
                        request.implementationPatchTarget(),
                        request.continuationConstraints(),
                        outline,
                        subtask,
                        request.eventJournal(),
                        attemptLedger,
                        detailFeedback.getOrDefault(subtask.id(), "")
                ));
            }
            ImplementationPlan finalPlan = planAssembler.assemble(outline, acceptedDetails);
            GateReport gateReport = implementationPlanGate.evaluate(
                    gateInputBuilder.build(
                            request.fingerprint(),
                            request.contractView(),
                            runtimeFacts,
                            request.qualityPlan(),
                            request.implementationPatchTarget(),
                            request.continuationConstraints(),
                            acceptedDetails.values().stream().toList(),
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
            request.eventJournal().append(ImplementationEventMessages.planningFinalGateReroute(
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
            PlanningRuntimeFacts runtimeFacts,
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
                        runtimeFacts,
                        continuationConstraints,
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
            PlanningRuntimeFacts runtimeFacts,
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
                    runtimeFacts,
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
                        deliveryPolicy,
                        runtimeFacts,
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
