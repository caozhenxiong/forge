package devflow.agent.orchestrator;

import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.loop.TransitionDecision;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewResult;
import devflow.agent.supervisor.SupervisorDecision;

/**
 * 统一维护 run 级 events.log 的中文消息模板。
 *
 * <p>events.log 主要给人读，所以这里优先保证：
 * 1. 中文可读；
 * 2. 同一类事件格式稳定；
 * 3. 关键流程字段都显式写出来，便于直接观察阶段流转。
 */
public final class WorkflowEventMessages {

    private WorkflowEventMessages() {
    }

    public static String runCreated() {
        return "运行｜已创建";
    }

    public static String enteringStage(StageType stageType, int attempt) {
        return "阶段｜进入开始｜阶段=%s｜尝试=%d".formatted(stageType, attempt);
    }

    public static String enteredStage(StageType stageType, int attempt) {
        return "阶段｜已进入｜阶段=%s｜尝试=%d".formatted(stageType, attempt);
    }

    public static String stageArtifactGenerated(StageType stageType, int attempt) {
        return "阶段｜产物生成完成｜阶段=%s｜尝试=%d".formatted(stageType, attempt);
    }

    public static String agentReviewed(StageType stageType, ReviewResult reviewResult) {
        return "阶段｜评审完成｜阶段=%s｜决定=%s｜修复模式=%s"
                .formatted(stageType, reviewResult.decision(), reviewResult.fixMode());
    }

    public static String supervisorDecided(SupervisorDecision supervisorDecision, boolean repeatedIssue, TransitionDecision transitionDecision) {
        return "阶段｜监督决策｜动作=%s｜目标阶段=%s｜模式=%s｜重复问题=%s｜原因=%s"
                .formatted(
                        supervisorDecision.action(),
                        supervisorDecision.targetStage(),
                        supervisorDecision.mode(),
                        repeatedIssue,
                        transitionDecision.reason()
                );
    }

    public static String stageContinued(TransitionDecision transitionDecision) {
        return "阶段｜继续执行｜阶段=%s｜目标阶段=%s｜原因=%s"
                .formatted(
                        transitionDecision.fromStage(),
                        transitionDecision.targetStage(),
                        transitionDecision.reason()
                );
    }

    public static String stageBlockedForHuman(TransitionDecision transitionDecision) {
        return "阶段｜等待人工处理｜阶段=%s｜目标阶段=%s｜原因=%s"
                .formatted(
                        transitionDecision.fromStage(),
                        transitionDecision.targetStage(),
                        transitionDecision.reason()
                );
    }

    public static String humanApproved(StageType stageType, String reviewer) {
        return "阶段｜人工批准｜阶段=%s｜审批人=%s".formatted(stageType, reviewer);
    }

    public static String maxAutoRevisionsExceeded(StageType stageType) {
        return "阶段｜超过最大自动修订次数｜阶段=%s".formatted(stageType);
    }

    public static String reroutedForRevision(StageType sourceStage, StageType rerouteStage, FixMode fixMode) {
        return "阶段｜回流｜来源阶段=%s｜目标阶段=%s｜修复模式=%s"
                .formatted(sourceStage, rerouteStage, fixMode);
    }

    public static String diagnosisTriggered(StageType sourceStage, String recommendedMode) {
        return "诊断｜已触发｜来源阶段=%s｜产物=%s｜推荐模式=%s"
                .formatted(sourceStage, AuxiliaryArtifactNames.REPAIR_BRIEF, recommendedMode);
    }

    public static String fatalFailure(StageType stageType, String reason) {
        return "阶段｜致命失败｜阶段=%s｜原因=%s".formatted(stageType, reason);
    }

    public static String stageOperationStarted(String operationKind, StageType stageType, int stageAttempt, int attempt, int maxAttempts) {
        return "阶段%s｜开始｜阶段=%s｜阶段尝试=%d｜本轮尝试=%d/%d"
                .formatted(operationKind, stageType, stageAttempt, attempt, maxAttempts);
    }

    public static String stageOperationHeartbeat(String operationKind, StageType stageType, int stageAttempt, int attempt, int maxAttempts) {
        return "阶段%s｜心跳｜阶段=%s｜阶段尝试=%d｜本轮尝试=%d/%d"
                .formatted(operationKind, stageType, stageAttempt, attempt, maxAttempts);
    }

    public static String stageOperationTimedOut(
            String operationKind,
            StageType stageType,
            int stageAttempt,
            int attempt,
            int maxAttempts,
            long timeoutSeconds
    ) {
        return "阶段%s｜超时｜阶段=%s｜阶段尝试=%d｜本轮尝试=%d/%d｜超时=%ds"
                .formatted(operationKind, stageType, stageAttempt, attempt, maxAttempts, timeoutSeconds);
    }

    public static String stageOperationAborted(
            String operationKind,
            StageType stageType,
            int stageAttempt,
            int attempt,
            int maxAttempts,
            String reason
    ) {
        return "阶段%s｜中止｜阶段=%s｜阶段尝试=%d｜本轮尝试=%d/%d｜原因=%s"
                .formatted(operationKind, stageType, stageAttempt, attempt, maxAttempts, reason);
    }

    public static String stageOperationFailed(
            String operationKind,
            StageType stageType,
            int stageAttempt,
            int attempt,
            int maxAttempts,
            Object failureType,
            String evidence
    ) {
        return "阶段%s｜失败｜阶段=%s｜阶段尝试=%d｜本轮尝试=%d/%d｜失败类型=%s｜证据=%s"
                .formatted(operationKind, stageType, stageAttempt, attempt, maxAttempts, failureType, evidence);
    }

    public static String stageOperationSucceeded(String operationKind, StageType stageType, int stageAttempt, int attempt, int maxAttempts) {
        return "阶段%s｜成功｜阶段=%s｜阶段尝试=%d｜本轮尝试=%d/%d"
                .formatted(operationKind, stageType, stageAttempt, attempt, maxAttempts);
    }
}
