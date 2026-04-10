package devflow.agent.supervisor;

import devflow.agent.context.ProjectedContext;
import devflow.agent.orchestrator.GatePolicy;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageFlowPolicy;
import devflow.agent.orchestrator.StageType;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一处理 supervisor 的阶段级保守决策。
 *
 * <p>这里专门负责“阶段通过/打回后应该怎么走”的确定性 fallback，
 * 避免同一 policy 同时还承担生成失败恢复和交付策略默认值拼装。
 */
final class SupervisorStageFallbackSupport {

    private final StageFlowPolicy stageFlowPolicy;

    SupervisorStageFallbackSupport(StageFlowPolicy stageFlowPolicy) {
        this.stageFlowPolicy = stageFlowPolicy;
    }

    SupervisorDecision decide(
            RunRecord runRecord,
            StageType currentStage,
            GatePolicy gatePolicy,
            ReviewResult reviewResult,
            boolean repeatedIssue,
            ProjectedContext projectedContext
    ) {
        StageType nextStage = stageFlowPolicy.nextStage(currentStage);
        if (reviewResult.decision() == ReviewDecision.APPROVED) {
            if (nextStage == null) {
                return new SupervisorDecision(
                        SupervisorAction.COMPLETE_RUN,
                        currentStage,
                        FixMode.NONE,
                        "最后阶段已通过，结束 run。",
                        List.of("归档最终产物"),
                        List.of(),
                        List.of("最终阶段通过证据"),
                        DeliveryPolicy.balanced(DeliveryPolicyMode.NONE),
                        false
                );
            }
            if (gatePolicy == GatePolicy.AGENT_PLUS_HUMAN) {
                return new SupervisorDecision(
                        SupervisorAction.REQUEST_HUMAN_REVIEW,
                        currentStage,
                        FixMode.NONE,
                        "当前阶段需要人工 gate，先阻塞等待人工批准。",
                        List.of("等待人工确认当前阶段产物"),
                        List.of("保持当前 artifact 不变"),
                        List.of("人工审批结果"),
                        DeliveryPolicy.balanced(DeliveryPolicyMode.NONE),
                        true
                );
            }
            DeliveryPolicy deliveryPolicy = nextStage == StageType.IMPLEMENTATION
                    ? initialImplementationPolicy(projectedContext)
                    : DeliveryPolicy.balanced(DeliveryPolicyMode.INCREMENTAL);
            return new SupervisorDecision(
                    SupervisorAction.ADVANCE_STAGE,
                    nextStage,
                    FixMode.NONE,
                    "当前阶段已通过，推进到下一阶段。",
                    List.of("进入 " + nextStage + " 并生成新产物"),
                    List.of(),
                    List.of("进入下一阶段所需的基础 artifact"),
                    deliveryPolicy,
                    false
            );
        }

        StageType retryStage = stageFlowPolicy.rerouteStage(currentStage, reviewResult.fixMode());
        SupervisorAction action = repeatedIssue && retryStage == StageType.IMPLEMENTATION
                ? SupervisorAction.ROUTE_TO_REPAIR
                : SupervisorAction.RETRY_STAGE;
        String reason = repeatedIssue
                ? "检测到重复问题，优先进入 repair 路径做定点修补。"
                : "当前问题仍可收敛，先按既定回退路径继续修订。";
        DeliveryPolicy deliveryPolicy;
        if (reviewResult.fixMode() == FixMode.PATCH) {
            deliveryPolicy = DeliveryPolicy.patchSafe();
        } else if (reviewResult.fixMode() == FixMode.REWORK) {
            deliveryPolicy = DeliveryPolicy.reworkSafe();
        } else {
            deliveryPolicy = DeliveryPolicy.balanced(DeliveryPolicyMode.INCREMENTAL);
        }
        return new SupervisorDecision(
                action,
                retryStage,
                reviewResult.fixMode(),
                reason,
                mergeNonBlank(reviewResult.summary(), reviewResult.changeRequest()),
                List.of("不要偏离当前 review 提示的主问题"),
                buildRequiredEvidence(reviewResult),
                deliveryPolicy,
                false
        );
    }

    DeliveryPolicy initialImplementationPolicy(ProjectedContext projectedContext) {
        boolean existingWorkingSet = projectedContext.workingSetSummary() != null
                && !projectedContext.workingSetSummary().isBlank()
                && !projectedContext.workingSetSummary().trim().isEmpty();
        return new DeliveryPolicy(
                existingWorkingSet ? DeliveryPolicyMode.INCREMENTAL : DeliveryPolicyMode.SKELETON,
                2,
                existingWorkingSet ? 4 : 3,
                true,
                true,
                true
        );
    }

    private List<String> buildRequiredEvidence(ReviewResult reviewResult) {
        List<String> items = new ArrayList<>();
        if (reviewResult.evidence() != null && !reviewResult.evidence().isBlank()) {
            items.add(reviewResult.evidence().trim());
        }
        if (reviewResult.actionItems() != null && !reviewResult.actionItems().isBlank()) {
            items.add(reviewResult.actionItems().trim());
        }
        if (items.isEmpty() && reviewResult.changeRequest() != null && !reviewResult.changeRequest().isBlank()) {
            items.add(reviewResult.changeRequest().trim());
        }
        return items;
    }

    private List<String> mergeNonBlank(String first, String second) {
        List<String> items = new ArrayList<>();
        if (first != null && !first.isBlank()) {
            items.add(first.trim());
        }
        if (second != null && !second.isBlank()) {
            items.add(second.trim());
        }
        return items;
    }
}
