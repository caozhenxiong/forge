package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;

/**
 * implementation 计划门面。
 *
 * <p>它只负责：
 * 1. 调用 planning prompt 组装器；
 * 2. 执行 planner turn loop；
 * 3. 触发 plan parser 与 gate；
 * 4. 产出最终 `ImplementationPlan`。
 *
 * <p>它不再继续承担长 prompt 模板、JSON repair 与 delivery mode 归一细节。
 */
class ImplementationPlanner {

    private final ImplementationPlanGate implementationPlanGate;
    private final int maxPlanParseAttempts;
    private final ImplementationPlanningPromptAssembler promptAssembler;
    private final ImplementationPlanningTurnRunner planningTurnRunner;

    ImplementationPlanner(
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            ImplementationPlanCoverageAnalyzer coverageAnalyzer,
            AgentTurnLoop agentTurnLoop,
            int maxPlanParseAttempts,
            int maxFilesPerSubtask,
            int maxDeliveryPolicyFiles
    ) {
        this.implementationPlanGate = new ImplementationPlanGate(coverageAnalyzer);
        this.maxPlanParseAttempts = maxPlanParseAttempts;
        this.promptAssembler = new ImplementationPlanningPromptAssembler(maxFilesPerSubtask, maxDeliveryPolicyFiles);
        ImplementationPlanParser planParser = new ImplementationPlanParser(llmProvider, objectMapper, maxPlanParseAttempts);
        this.planningTurnRunner = new ImplementationPlanningTurnRunner(
                llmProvider,
                agentTurnLoop,
                planParser,
                implementationPlanGate
        );
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
            ImplementationContinuationConstraints continuationConstraints
    ) {
        String planningFeedback = "";
        for (int attempt = 1; attempt <= maxPlanParseAttempts; attempt++) {
            ImplementationPlanningPrompt prompt = promptAssembler.assemble(
                    runRecord,
                    analysis,
                    prd,
                    design,
                    note,
                    workspaceContext,
                    plannerContextMarkdown,
                    performanceValidationGuidance,
                    preferSkeletonFlow,
                    deliveryPolicy,
                    contractView,
                    qualityPlan,
                    language,
                    fixMode,
                    implementationPatchTarget,
                    requirementCatalog,
                    continuationConstraints,
                    planningFeedback
            );
            ImplementationPlanningTurnRunner.PlanningTurnOutcome planningTurnOutcome = planningTurnRunner.run(
                    projectPath.getFileName() + "#plan-" + attempt,
                    prompt.systemPrompt(),
                    prompt.userPrompt(),
                    fixMode,
                    preferSkeletonFlow,
                    deliveryPolicy,
                    contractView,
                    qualityPlan,
                    fingerprint,
                    implementationPatchTarget,
                    continuationConstraints
            );
            ImplementationPlan plan = planningTurnOutcome.plan();
            GateReport gateReport = planningTurnOutcome.gateReport();
            if (gateReport.passed()) {
                return plan;
            }
            planningFeedback = implementationPlanGate.toPlanningFeedback(gateReport);
            if (attempt == maxPlanParseAttempts) {
                throw new ImplementationPlanningException(
                        ImplementationPlanningFailureReason.COVERAGE_MISMATCH,
                        "Implementation plan does not satisfy execution contract: " + planningFeedback
                );
            }
        }
        throw new ImplementationPlanningException(
                ImplementationPlanningFailureReason.PLAN_GENERATION_EXHAUSTED,
                "Implementation plan generation failed for " + projectPath
        );
    }
}
