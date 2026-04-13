package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationBudgetProfile;
import devflow.agent.executor.generation.GenerationTelemetry;
import devflow.agent.executor.llm.LlmGenerateRequest;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;

import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.loop.AgentTurnSnapshot;
import devflow.agent.loop.AgentTurnState;
import devflow.agent.loop.AgentTurnStepResult;
import java.util.concurrent.atomic.AtomicReference;

/**
 * implementation planning 的单轮生成执行器。
 *
 * <p>它只负责：
 * 1. 走完 planner 的 prepare/execute/observe 状态机；
 * 2. 调模型拿到当前单元的原始响应；
 * 3. 回传本轮 telemetry。
 *
 * <p>解析、gate、重试和状态持久化都由 {@link ImplementationPlanner} 统一负责。
 */
final class ImplementationPlanningTurnRunner {

    private final LlmProvider llmProvider;
    private final AgentTurnLoop agentTurnLoop;

    ImplementationPlanningTurnRunner(
            LlmProvider llmProvider,
            AgentTurnLoop agentTurnLoop
    ) {
        this.llmProvider = llmProvider;
        this.agentTurnLoop = agentTurnLoop;
    }

    PlanningTurnOutcome run(
            String unitLabel,
            String systemPrompt,
            String userPrompt
    ) {
        AtomicReference<String> responseRef = new AtomicReference<>("");
        AtomicReference<GenerationTelemetry> telemetryRef = new AtomicReference<>();
        AgentTurnSnapshot snapshot = agentTurnLoop.runUntilSettled(
                AgentTurnSnapshot.start(),
                current -> {
                    AgentTurnState state = current.state();
                    if (state == AgentTurnState.IDLE) {
                        return AgentTurnStepResult.advance(current.next(AgentTurnState.PREPARE_CONTEXT, unitLabel, "prepare-implementation-planning-unit"));
                    }
                    if (state == AgentTurnState.PREPARE_CONTEXT) {
                        return AgentTurnStepResult.advance(current.next(AgentTurnState.EXECUTE_STEP, unitLabel, "invoke-implementation-planning-unit"));
                    }
                    if (state == AgentTurnState.EXECUTE_STEP) {
                        responseRef.set(llmProvider.generate(LlmGenerateRequest.workingPrompt(
                                systemPrompt,
                                userPrompt,
                                LlmOptions.outputBudgetRatio(GenerationBudgetProfile.implementationPlanOutputRatio()),
                                ModelRole.IMPLEMENTATION
                        )));
                        telemetryRef.set(llmProvider.consumeLastTelemetry());
                        return AgentTurnStepResult.stop(current.next(
                                AgentTurnState.COMPLETE,
                                unitLabel,
                                "implementation-planning-unit-generated"
                        ));
                    }
                    return AgentTurnStepResult.stop(current.next(AgentTurnState.FAILED, unitLabel, "unexpected-planning-unit-state"));
                }
        );
        return new PlanningTurnOutcome(snapshot, responseRef.get(), telemetryRef.get());
    }

    record PlanningTurnOutcome(
            AgentTurnSnapshot snapshot,
            String response,
            GenerationTelemetry telemetry
    ) {
    }
}
