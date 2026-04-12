package devflow.agent.executor;

import devflow.agent.review.ReviewDecision;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 统一维护 implementation 事件流的人类可读中文消息模板。
 *
 * <p>这些消息会同时进入 `implementation_events.md` 和 run 级 `events.log`，
 * 这里优先保证事件对人可读，同时保留关键字段方便追踪问题。
 */
public final class ImplementationEventMessages {

    private ImplementationEventMessages() {
    }

    public static String subtaskStart(String title, DeliveryMode mode, String files) {
        return "实现阶段｜子任务开始｜标题=%s｜交付模式=%s｜文件=%s".formatted(title, mode, files);
    }

    public static String subtaskFinished(String title, boolean completed, int attempts) {
        return "实现阶段｜子任务结束｜标题=%s｜完成=%s｜总尝试=%d".formatted(title, completed, attempts);
    }

    public static String subtaskAttemptStart(String title, int attempt, int maxAttempts) {
        return "实现阶段｜子任务尝试开始｜标题=%s｜尝试=%d/%d".formatted(title, attempt, maxAttempts);
    }

    public static String subtaskAttemptApproved(String title, int attempt, int maxAttempts) {
        return "实现阶段｜子任务尝试通过｜标题=%s｜尝试=%d/%d".formatted(title, attempt, maxAttempts);
    }

    public static String subtaskAttemptRejected(String title, int attempt, int maxAttempts, ReviewDecision decision) {
        return "实现阶段｜子任务尝试驳回｜标题=%s｜尝试=%d/%d｜评审决定=%s"
                .formatted(title, attempt, maxAttempts, decision);
    }

    public static String fileApplyStart(Path relativePath, ChangeAction action, DeliveryMode mode) {
        return "实现阶段｜文件写入开始｜路径=%s｜动作=%s｜交付模式=%s".formatted(relativePath, action, mode);
    }

    public static String fileApplyFinished(Path relativePath, ChangeAction action) {
        return "实现阶段｜文件写入完成｜路径=%s｜动作=%s".formatted(relativePath, action);
    }

    public static String splitUnit(Path relativePath, String strategy, String unitLabel, GenerationFailureType failureType) {
        return "生成｜单元拆分｜文件=%s｜策略=%s｜单元=%s｜原因=%s"
                .formatted(relativePath, strategy, unitLabel, failureType);
    }

    public static String expandScaffold(Path relativePath, String strategy, String unitLabel, String followUpUnits) {
        return "生成｜骨架扩展｜文件=%s｜策略=%s｜单元=%s｜后续单元=%s"
                .formatted(relativePath, strategy, unitLabel, followUpUnits);
    }

    public static String externalizeInlineScript(Path relativePath, String strategy, GenerationFailureType failureType) {
        return "生成｜内联脚本外提｜文件=%s｜策略=%s｜原因=%s"
                .formatted(relativePath, strategy, failureType);
    }

    public static String externalizeInlineScript(Path relativePath, String strategy, String reason) {
        return "生成｜内联脚本外提｜文件=%s｜策略=%s｜原因=%s"
                .formatted(relativePath, strategy, reason == null || reason.isBlank() ? "unspecified" : reason);
    }

    public static String generationStarted(String operation, Path relativePath, String strategy, DeliveryMode mode, int attempt, int maxAttempts) {
        return "生成｜开始｜操作=%s｜文件=%s｜策略=%s｜交付模式=%s｜尝试=%d/%d"
                .formatted(operation, relativePath, strategy, mode, attempt, maxAttempts);
    }

    public static String generationHeartbeat(String operation, Path relativePath, String strategy, DeliveryMode mode, int attempt, int maxAttempts) {
        return "生成｜心跳｜操作=%s｜文件=%s｜策略=%s｜交付模式=%s｜尝试=%d/%d"
                .formatted(operation, relativePath, strategy, mode, attempt, maxAttempts);
    }

    public static String generationFailed(
            String operation,
            Path relativePath,
            String strategy,
            DeliveryMode mode,
            int attempt,
            int maxAttempts,
            GenerationFailureType type,
            String evidence
    ) {
        return "生成｜失败｜操作=%s｜文件=%s｜策略=%s｜交付模式=%s｜尝试=%d/%d｜失败类型=%s｜证据=%s"
                .formatted(operation, relativePath, strategy, mode, attempt, maxAttempts, type, evidence);
    }

    public static String generationTimedOut(
            String operation,
            Path relativePath,
            String strategy,
            DeliveryMode mode,
            int attempt,
            int maxAttempts,
            Duration timeout
    ) {
        return "生成｜超时｜操作=%s｜文件=%s｜策略=%s｜交付模式=%s｜尝试=%d/%d｜超时=%ds"
                .formatted(operation, relativePath, strategy, mode, attempt, maxAttempts, timeout.toSeconds());
    }

    public static String generationAborted(
            String operation,
            Path relativePath,
            String strategy,
            DeliveryMode mode,
            int attempt,
            int maxAttempts,
            String reason
    ) {
        return "生成｜中止｜操作=%s｜文件=%s｜策略=%s｜交付模式=%s｜尝试=%d/%d｜原因=%s"
                .formatted(operation, relativePath, strategy, mode, attempt, maxAttempts, reason);
    }

    public static String generationSucceeded(String operation, Path relativePath, String strategy, DeliveryMode mode, int attempt, int maxAttempts) {
        return "生成｜成功｜操作=%s｜文件=%s｜策略=%s｜交付模式=%s｜尝试=%d/%d"
                .formatted(operation, relativePath, strategy, mode, attempt, maxAttempts);
    }

    public static String reviewStarted(String subtaskTitle, int attempt, int maxAttempts) {
        return "实现评审｜开始｜子任务=%s｜尝试=%d/%d".formatted(subtaskTitle, attempt, maxAttempts);
    }

    public static String reviewHeartbeat(String subtaskTitle, int attempt, int maxAttempts) {
        return "实现评审｜心跳｜子任务=%s｜尝试=%d/%d".formatted(subtaskTitle, attempt, maxAttempts);
    }

    public static String reviewTimedOut(String subtaskTitle, int attempt, int maxAttempts, Duration timeout) {
        return "实现评审｜超时｜子任务=%s｜尝试=%d/%d｜超时=%ds"
                .formatted(subtaskTitle, attempt, maxAttempts, timeout.toSeconds());
    }

    public static String reviewAborted(String subtaskTitle, int attempt, int maxAttempts, String reason) {
        return "实现评审｜中止｜子任务=%s｜尝试=%d/%d｜原因=%s"
                .formatted(subtaskTitle, attempt, maxAttempts, reason);
    }

    public static String reviewFailed(String subtaskTitle, int attempt, int maxAttempts, GenerationFailureType failureType, String evidence) {
        return "实现评审｜失败｜子任务=%s｜尝试=%d/%d｜失败类型=%s｜证据=%s"
                .formatted(subtaskTitle, attempt, maxAttempts, failureType, evidence);
    }

    public static String reviewSucceeded(String subtaskTitle, int attempt, int maxAttempts) {
        return "实现评审｜成功｜子任务=%s｜尝试=%d/%d".formatted(subtaskTitle, attempt, maxAttempts);
    }

    public static String reusingPreviousPlan(int completedPrefix, int plannedSubtasks) {
        return "实现阶段｜复用旧计划｜已完成前缀=%d｜计划子任务=%d".formatted(completedPrefix, plannedSubtasks);
    }

    public static String planningUnitStarted(
            ImplementationPlanningUnitKind unitKind,
            String unitId,
            int attempt,
            int maxAttempts
    ) {
        return "实现规划｜单元开始｜类型=%s｜单元=%s｜尝试=%d/%d"
                .formatted(unitKind, unitId, attempt, maxAttempts);
    }

    public static String planningUnitAccepted(
            ImplementationPlanningUnitKind unitKind,
            String unitId,
            int attempt,
            int maxAttempts,
            GenerationTelemetry telemetry
    ) {
        return "实现规划｜单元通过｜类型=%s｜单元=%s｜尝试=%d/%d%s"
                .formatted(unitKind, unitId, attempt, maxAttempts, GenerationTelemetryFormatter.renderInline(telemetry));
    }

    public static String planningUnitRejected(
            ImplementationPlanningUnitKind unitKind,
            String unitId,
            int attempt,
            int maxAttempts,
            String reason,
            GenerationTelemetry telemetry
    ) {
        return "实现规划｜单元驳回｜类型=%s｜单元=%s｜尝试=%d/%d｜原因=%s%s"
                .formatted(
                        unitKind,
                        unitId,
                        attempt,
                        maxAttempts,
                        reason == null || reason.isBlank() ? "未知" : reason,
                        GenerationTelemetryFormatter.renderInline(telemetry)
                );
    }

    public static String planningUnitRepairApplied(
            ImplementationPlanningUnitKind unitKind,
            String unitId
    ) {
        return "实现规划｜单元修复｜类型=%s｜单元=%s｜来源=repair-model"
                .formatted(unitKind, unitId);
    }

    public static String planningFinalGateReroute(
            ImplementationPlanningUnitKind unitKind,
            String unitId,
            String reason
    ) {
        return "实现规划｜最终 gate 回退｜目标=%s｜单元=%s｜原因=%s"
                .formatted(unitKind, unitId, reason == null || reason.isBlank() ? "未知" : reason);
    }

    public static String repairTrace(
            String step,
            Path relativePath,
            String unitLabel,
            String result,
            String evidence
    ) {
        return "修复｜步骤=%s｜文件=%s｜单元=%s｜结果=%s｜证据=%s"
                .formatted(step, relativePath, unitLabel, result, evidence == null || evidence.isBlank() ? "无" : evidence);
    }
}
