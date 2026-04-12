package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.validation.ProjectFingerprint;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 单个子任务 detail 的确定性 gate。
 *
 * <p>它负责把 detail 限定在当前 outline 子任务的边界内：
 * 1. subtaskId 必须一致；
 * 2. changes 不能为空，且不能越出 targetPaths；
 * 3. 局部 runtime / continuation 声明必须自洽。
 */
final class ImplementationSubtaskDetailGate {

    private static final String DETAIL_RETRY_GUIDANCE = """
            请重新规划当前子任务的 detail，并确保：
            1. changes 只允许覆盖当前子任务自己的 targetPaths
            2. 不要改动当前子任务 targetPaths 之外的文件
            3. HTML/runtime wiring 字段必须完整自洽
            4. 只返回当前子任务自己的结构化 detail JSON
            """;

    private final ImplementationPlanChangeGate changeGate;

    ImplementationSubtaskDetailGate(ImplementationPlanChangeGate changeGate) {
        this.changeGate = changeGate;
    }

    GateReport evaluate(
            ProjectFingerprint fingerprint,
            ContractView contractView,
            DeliveryPolicyEnvelope deliveryPolicy,
            ImplementationContinuationConstraints continuationConstraints,
            ImplementationOutlineSubtask outlineSubtask,
            ImplementationSubtaskDetail detail
    ) {
        List<GateIssue> issues = new ArrayList<>();
        int issueIndex = 1;
        if (outlineSubtask == null) {
            return GateReport.failure(
                    "子任务 detail 缺少 outline 上下文。",
                    List.of(new GateIssue(
                            "PLAN_DETAIL_1",
                            "Implementation detail 必须绑定到已有 outline 子任务。",
                            GateFailureDisposition.REPLAN_CURRENT_STAGE
                    ))
            );
        }
        String expectedId = blankIfNull(outlineSubtask.id());
        String actualId = detail == null ? "" : blankIfNull(detail.subtaskId());
        if (!expectedId.equals(actualId)) {
            issues.add(new GateIssue(
                    "PLAN_DETAIL_" + issueIndex++,
                    "Implementation detail 的 subtaskId 必须与当前 outline 子任务一致: " + expectedId,
                    GateFailureDisposition.REPLAN_CURRENT_STAGE
            ));
        }
        List<FileChange> changes = detail == null || detail.changes() == null ? List.of() : detail.changes();
        if (changes.isEmpty()) {
            issues.add(new GateIssue(
                    "PLAN_DETAIL_" + issueIndex++,
                    "Implementation detail 必须至少声明一个文件变更。",
                    GateFailureDisposition.REPLAN_CURRENT_STAGE
            ));
        }
        Set<String> changedPaths = new LinkedHashSet<>();
        for (FileChange change : changes) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                issues.add(new GateIssue(
                        "PLAN_DETAIL_" + issueIndex++,
                        "Implementation detail 不能包含空路径变更。",
                        GateFailureDisposition.REPLAN_CURRENT_STAGE
                ));
                continue;
            }
            changedPaths.add(change.path().trim());
            if (!safeList(outlineSubtask.targetPaths()).contains(change.path().trim())) {
                issues.add(new GateIssue(
                        "PLAN_DETAIL_" + issueIndex++,
                        "Implementation detail 不能越界修改非当前子任务 targetPaths 的文件: " + change.path(),
                        GateFailureDisposition.REPLAN_CURRENT_STAGE,
                        GateIssueContext.forPath(change.path())
                ));
            }
        }
        if (deliveryPolicy != null && changedPaths.size() > deliveryPolicy.maxFiles()) {
            issues.add(new GateIssue(
                    "PLAN_DETAIL_" + issueIndex++,
                    "当前子任务 detail 不能超过 %d 个文件。".formatted(deliveryPolicy.maxFiles()),
                    GateFailureDisposition.REPLAN_CURRENT_STAGE,
                    GateIssueContext.forPaths(List.copyOf(changedPaths))
            ));
        }
        Subtask subtask = new Subtask(
                outlineSubtask.title(),
                outlineSubtask.goal(),
                safeList(outlineSubtask.coverageRefs()),
                safeList(outlineSubtask.ownedCapabilities()),
                safeList(outlineSubtask.deferredCapabilities()),
                safeList(outlineSubtask.acceptanceCriteria()),
                outlineSubtask.runnableMilestone(),
                outlineSubtask.deliveryMode(),
                changes
        );
        issues.addAll(changeGate.evaluate(
                fingerprint,
                contractView,
                continuationConstraints,
                List.of(subtask)
        ));
        if (issues.isEmpty()) {
            return GateReport.success();
        }
        return GateReport.failure("当前子任务 detail 未通过结构校验。", issues);
    }

    String toRetryFeedback(GateReport report) {
        return report.toRetryFeedback(DETAIL_RETRY_GUIDANCE);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }
}
