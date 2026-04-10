package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.loop.AgentTurnSnapshot;
import devflow.agent.loop.AgentTurnState;
import devflow.agent.loop.AgentTurnStepResult;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.validation.ProjectFingerprint;
import java.util.concurrent.atomic.AtomicReference;

/**
 * implementation planning 的单轮 turn 执行器。
 *
 * <p>planner 的 prepare/execute/observe/evaluate 状态机下沉到这里后，
 * `ImplementationPlanner` 只保留 prompt、parser 和 gate 的高层编排。
 */
final class ImplementationPlanningTurnRunner {

    private final LlmProvider llmProvider;
    private final AgentTurnLoop agentTurnLoop;
    private final ImplementationPlanParser planParser;
    private final ImplementationPlanGate implementationPlanGate;
    private final ImplementationPlanGateInputBuilder gateInputBuilder;

    ImplementationPlanningTurnRunner(
            LlmProvider llmProvider,
            AgentTurnLoop agentTurnLoop,
            ImplementationPlanParser planParser,
            ImplementationPlanGate implementationPlanGate
    ) {
        this.llmProvider = llmProvider;
        this.agentTurnLoop = agentTurnLoop;
        this.planParser = planParser;
        this.implementationPlanGate = implementationPlanGate;
        this.gateInputBuilder = new ImplementationPlanGateInputBuilder();
    }

    PlanningTurnOutcome run(
            String unitLabel,
            String systemPrompt,
            String userPrompt,
            FixMode fixMode,
            boolean preferSkeletonFlow,
            DeliveryPolicyEnvelope deliveryPolicy,
            ContractView contractView,
            QualityPlan qualityPlan,
            ProjectFingerprint fingerprint,
            ImplementationContinuationConstraints continuationConstraints
    ) {
        AtomicReference<String> responseRef = new AtomicReference<>("");
        AtomicReference<ImplementationPlan> planRef = new AtomicReference<>();
        AtomicReference<GateReport> gateReportRef = new AtomicReference<>();
        AgentTurnSnapshot snapshot = agentTurnLoop.runUntilSettled(
                AgentTurnSnapshot.start(),
                current -> {
                    AgentTurnState state = current.state();
                    if (state == AgentTurnState.IDLE) {
                        return AgentTurnStepResult.advance(current.next(AgentTurnState.PREPARE_CONTEXT, unitLabel, "prepare-implementation-plan"));
                    }
                    if (state == AgentTurnState.PREPARE_CONTEXT) {
                        return AgentTurnStepResult.advance(current.next(AgentTurnState.EXECUTE_STEP, unitLabel, "invoke-implementation-planner"));
                    }
                    if (state == AgentTurnState.EXECUTE_STEP) {
                        responseRef.set(llmProvider.generate(
                                systemPrompt,
                                userPrompt,
                                LlmOptions.outputBudgetRatio(GenerationBudgetProfile.implementationPlanOutputRatio()),
                                ModelRole.IMPLEMENTATION
                        ));
                        return AgentTurnStepResult.advance(current.next(AgentTurnState.OBSERVE_RESULT, unitLabel, "implementation-plan-generated"));
                    }
                    if (state == AgentTurnState.OBSERVE_RESULT) {
                        planRef.set(planParser.parsePlanWithRepair(
                                responseRef.get(),
                                fixMode,
                                preferSkeletonFlow,
                                deliveryPolicy,
                                contractView == null ? null : contractView.productContract(),
                                contractView == null ? null : contractView.executionContract(),
                                qualityPlan,
                                continuationConstraints
                        ));
                        return AgentTurnStepResult.advance(current.next(AgentTurnState.EVALUATE_RESULT, unitLabel, "validate-implementation-plan"));
                    }
                    if (state == AgentTurnState.EVALUATE_RESULT) {
                        GateReport gateReport = implementationPlanGate.evaluate(
                                gateInputBuilder.build(
                                        fingerprint,
                                        contractView,
                                        qualityPlan,
                                        continuationConstraints,
                                        planRef.get()
                                )
                        );
                        gateReportRef.set(gateReport);
                        return AgentTurnStepResult.stop(current.next(
                                gateReport.passed() ? AgentTurnState.COMPLETE : AgentTurnState.REQUEST_CONTINUATION,
                                unitLabel,
                                gateReport.passed() ? "implementation-plan-accepted" : "implementation-plan-needs-repair"
                        ));
                    }
                    return AgentTurnStepResult.stop(current.next(AgentTurnState.FAILED, unitLabel, "unexpected-planner-state"));
                }
        );
        return new PlanningTurnOutcome(snapshot, planRef.get(), gateReportRef.get());
    }

    record PlanningTurnOutcome(
            AgentTurnSnapshot snapshot,
            ImplementationPlan plan,
            GateReport gateReport
    ) {
    }
}
