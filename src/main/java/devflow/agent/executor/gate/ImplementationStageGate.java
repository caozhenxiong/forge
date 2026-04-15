package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.implementation.CompletedPlanPatchOwnerResolver;
import devflow.agent.executor.implementation.planning.ImplementationPlan;
import devflow.agent.executor.runtime.*;

import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewRevisionRoute;
import java.util.ArrayList;
import java.util.List;

import devflow.agent.executor.subtask.SubtaskAttemptReport;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
/**
 * 负责 implementation 阶段的整体 gate 判断。
 *
 * <p>这个类只关心“计划是否完成、阶段是否可推进”，不参与代码生成与文件写入。
 * 它不再追加 synthetic architect 子任务；阶段级 contract 结果只通过单一 gate 结构对外传播。
 */
public class ImplementationStageGate {

    private final CompletedPlanPatchOwnerResolver completedPlanPatchOwnerResolver =
            new CompletedPlanPatchOwnerResolver();
    private final RuntimeWiringRetryChangeFactory runtimeWiringRetryChangeFactory = new RuntimeWiringRetryChangeFactory();

    /**
     * 汇总当前 implementation 尝试的整体完成状态。
     */
    public ImplementationStageStatus summarizeStageStatus(
            ImplementationPlan plan,
            List<SubtaskExecutionReport> reports,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        int plannedSubtasks = plan == null || plan.subtasks() == null ? 0 : plan.subtasks().size();
        int executedSubtasks = Math.min(plannedSubtasks, reports == null ? 0 : reports.size());
        int completedSubtasks = 0;
        if (reports != null) {
            for (int index = 0; index < executedSubtasks; index++) {
                if (reports.get(index) != null && reports.get(index).completed()) {
                    completedSubtasks++;
                }
            }
        }
        List<String> incompleteSubtasks = new ArrayList<>();
        if (plan != null && plan.subtasks() != null) {
            for (int index = 0; index < plan.subtasks().size(); index++) {
                if (index >= executedSubtasks) {
                    incompleteSubtasks.add(plan.subtasks().get(index).title());
                    continue;
                }
                SubtaskExecutionReport report = reports.get(index);
                if (report == null || !report.completed()) {
                    incompleteSubtasks.add(plan.subtasks().get(index).title());
                }
            }
        }
        boolean planCompleted = plannedSubtasks > 0
                && executedSubtasks == plannedSubtasks
                && completedSubtasks == plannedSubtasks;
        boolean stageReady = planCompleted && (contractGateResult == null || contractGateResult.passed());
        List<String> remainingSubtasks = List.copyOf(incompleteSubtasks);
        ContinuationDisposition continuationDisposition = continuationDisposition(
                reports,
                planCompleted,
                remainingSubtasks,
                contractGateResult
        );
        continuationDisposition = enforceCompletedPlanPatchOwnership(
                planCompleted,
                reports,
                contractGateResult,
                continuationDisposition
        );
        return new ImplementationStageStatus(
                plannedSubtasks,
                executedSubtasks,
                completedSubtasks,
                planCompleted,
                stageReady,
                remainingSubtasks,
                contractGateResult,
                continuationDisposition == null
                        ? ImplementationContinuationMode.MID_PLAN_CONTINUE
                        : continuationDisposition.mode(),
                continuationDisposition == null ? "" : continuationDisposition.summary(),
                continuationDisposition == null ? "" : continuationDisposition.changeRequest(),
                continuationDisposition == null ? "" : continuationDisposition.evidence(),
                continuationDisposition == null ? "" : continuationDisposition.actionItems(),
                continuationDisposition == null ? List.of() : continuationDisposition.overrideChanges(),
                continuationDisposition == null
                        ? ImplementationPatchTarget.NONE
                        : continuationDisposition.implementationPatchTarget(),
                continuationDisposition == null ? ReviewReasonCode.NONE : continuationDisposition.reasonCode()
        );
    }

    private ContinuationDisposition continuationDisposition(
            List<SubtaskExecutionReport> reports,
            boolean planCompleted,
            List<String> incompleteSubtasks,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        ContinuationDisposition reportDisposition = latestReportContinuationDisposition(reports, contractGateResult);
        if (reportDisposition != null) {
            return reportDisposition;
        }
        if (!planCompleted) {
            return incompletePlanContinuation(incompleteSubtasks);
        }
        if (contractGateResult == null || contractGateResult.passed()) {
            return null;
        }
        return contractGateContinuation(reports, contractGateResult, incompleteSubtasks);
    }

    private ContinuationDisposition latestReportContinuationDisposition(
            List<SubtaskExecutionReport> reports,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        for (int index = reports.size() - 1; index >= 0; index--) {
            SubtaskExecutionReport report = reports.get(index);
            if (report == null || report.attempts() == null || report.attempts().isEmpty()) {
                continue;
            }
            SubtaskAttemptReport latest = report.attempts().get(report.attempts().size() - 1);
            ReviewResult review = canonicalizeContinuationReview(report, latest.review(), contractGateResult);
            if (review == null) {
                continue;
            }
            if (requiresCanonicalRepairPackage(review) && !hasSafeRepairPackage(report)) {
                return continuationDisposition(
                        report,
                        missingScopeReview(review),
                        ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK,
                        "当前实现需要继续 patch，但阶段汇总没有拿到结构化文件范围，不能自动续跑。"
                );
            }
            if (requiresResolvedRuntimeContract(review) && !hasResolvedRuntimeContract(contractGateResult)) {
                return continuationDisposition(
                        report,
                        missingRuntimeContractReview(review),
                        ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK,
                        "当前实现需要继续修复 runtime wiring，但阶段汇总没有拿到有效的 runtime contract，不能自动续跑。"
                );
            }
            if (review.revisionRoute() == devflow.agent.review.ReviewRevisionRoute.REQUEST_HUMAN) {
                return continuationDisposition(
                        report,
                        review,
                        ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK,
                        blank(review.summary()).isBlank()
                                ? "实现阶段遇到确定性工具阻塞，当前不能继续自动续跑。"
                                : review.summary()
                );
            }
            if ((review.revisionRoute() == ReviewRevisionRoute.PATCH_CURRENT_STAGE
                    || review.revisionRoute() == ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET)
                    && review.fixMode() == FixMode.PATCH
                    && review.implementationPatchTarget().concretePatch()) {
                if (review.implementationPatchTarget() == ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                        && review.overrideChanges().isEmpty()) {
                    return continuationDisposition(
                            report,
                            missingScopeReview(review),
                            ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK,
                            "当前实现需要继续 patch，但阶段汇总没有拿到结构化文件范围，不能自动续跑。"
                    );
                }
                return continuationDisposition(
                        report,
                        review,
                        ImplementationContinuationMode.PATCH_CONTINUE,
                        blank(review.summary()).isBlank()
                                ? "实现阶段需要继续修补当前子任务，再重新验证。"
                                : review.summary()
                );
            }
        }
        return null;
    }

    private ContinuationDisposition enforceCompletedPlanPatchOwnership(
            boolean planCompleted,
            List<SubtaskExecutionReport> reports,
            ArchitectIntegrationCheckResult contractGateResult,
            ContinuationDisposition continuationDisposition
    ) {
        if (!planCompleted
                || continuationDisposition == null
                || continuationDisposition.mode() != ImplementationContinuationMode.PATCH_CONTINUE
                || !continuationDisposition.implementationPatchTarget().concretePatch()) {
            return continuationDisposition;
        }
        if (continuationDisposition.implementationPatchTarget() == ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                && completedPlanPatchOwnerResolver.hasSingleCompletedOwner(reports, continuationDisposition.overrideChanges())) {
            return continuationDisposition;
        }
        if (continuationDisposition.implementationPatchTarget() == ImplementationPatchTarget.PATCH_RUNTIME_WIRING
                && completedPlanPatchOwnerResolver.hasResolvableRuntimeWiringOwner(
                        reports,
                        contractGateResult == null ? null : contractGateResult.runtimeContract()
                )) {
            return continuationDisposition;
        }
        String evidence = ownershipEvidence(continuationDisposition, contractGateResult);
        if (continuationDisposition.implementationPatchTarget() == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            return new ContinuationDisposition(
                    ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK,
                    "当前 runtime wiring patch package 不能唯一映射到已完成子任务 owner，不能自动续跑。",
                    "请先把 runtime wiring patch 收敛到唯一的 completed owner；无法唯一定位时转人工。",
                    evidence,
                    "1. 优先按 companion runtime roots 反查唯一 completed owner。 2. 只有没有 runtime roots 时才允许按唯一 html owner 恢复。 3. 无法唯一定位时转人工。",
                    List.of(),
                    ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                    continuationDisposition.reasonCode() == null
                            ? ReviewReasonCode.RUNTIME_WIRING_GAP
                            : continuationDisposition.reasonCode()
            );
        }
        return new ContinuationDisposition(
                ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK,
                "当前 patch package 跨越多个已完成子任务 owner，不能自动续跑。",
                "请先把 overrideChanges 收敛到单个 completed subtask 的 declared owner 文件范围，再恢复 implementation 续跑。",
                evidence,
                "1. 按单个 completed subtask 的 declared owner files 收敛 patch package。 2. 无法收敛时转人工。 3. 收口后再恢复 implementation。",
                List.of(),
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                continuationDisposition.reasonCode() == null ? ReviewReasonCode.IMPLEMENTATION_GAP : continuationDisposition.reasonCode()
        );
    }

    private ReviewResult canonicalizeContinuationReview(
            SubtaskExecutionReport report,
            ReviewResult review,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        if (requiresResolvedRuntimeContract(review) && hasResolvedRuntimeContract(contractGateResult)) {
            return withOverrideChanges(review, runtimeWiringRetryChangeFactory.build(contractGateResult.runtimeContract()));
        }
        if (!requiresCanonicalRepairPackage(review)) {
            return review;
        }
        List<FileChange> allowedScope = structuredRepairScope(report);
        if (allowedScope.isEmpty()) {
            return withOverrideChanges(review, List.of());
        }
        List<FileChange> canonicalScope = canonicalOverrideChanges(review.overrideChanges(), allowedScope);
        return withOverrideChanges(review, canonicalScope);
    }

    private List<FileChange> canonicalOverrideChanges(
            List<FileChange> proposedChanges,
            List<FileChange> allowedScope
    ) {
        if (allowedScope == null || allowedScope.isEmpty()) {
            return List.of();
        }
        if (proposedChanges == null || proposedChanges.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashMap<java.nio.file.Path, FileChange> allowedByPath = new java.util.LinkedHashMap<>();
        for (FileChange change : allowedScope) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            allowedByPath.put(java.nio.file.Path.of(change.path()).normalize(), change);
        }
        java.util.LinkedHashSet<java.nio.file.Path> acceptedPaths = new java.util.LinkedHashSet<>();
        for (FileChange change : proposedChanges) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            java.nio.file.Path normalizedPath = java.nio.file.Path.of(change.path()).normalize();
            if (allowedByPath.containsKey(normalizedPath)) {
                acceptedPaths.add(normalizedPath);
            }
        }
        if (acceptedPaths.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<FileChange> canonical = new java.util.ArrayList<>();
        for (FileChange change : allowedScope) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            java.nio.file.Path normalizedPath = java.nio.file.Path.of(change.path()).normalize();
            if (acceptedPaths.contains(normalizedPath)) {
                canonical.add(change);
            }
        }
        return List.copyOf(canonical);
    }

    private boolean requiresCanonicalRepairPackage(ReviewResult review) {
        if (review == null || review.fixMode() != FixMode.PATCH) {
            return false;
        }
        if (review.implementationPatchTarget() != ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION) {
            return false;
        }
        return review.revisionRoute() == ReviewRevisionRoute.PATCH_CURRENT_STAGE
                || review.revisionRoute() == ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET;
    }

    private boolean requiresResolvedRuntimeContract(ReviewResult review) {
        if (review == null || review.fixMode() != FixMode.PATCH) {
            return false;
        }
        if (review.implementationPatchTarget() != ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            return false;
        }
        return review.revisionRoute() == ReviewRevisionRoute.PATCH_CURRENT_STAGE
                || review.revisionRoute() == ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET;
    }

    private boolean hasSafeRepairPackage(SubtaskExecutionReport report) {
        return !structuredRepairScope(report).isEmpty();
    }

    private boolean hasResolvedRuntimeContract(ArchitectIntegrationCheckResult contractGateResult) {
        HtmlRuntimeOwnershipContract runtimeContract = contractGateResult == null ? null : contractGateResult.runtimeContract();
        return runtimeContract != null && runtimeContract.hasResolvedWiringRepairScope();
    }

    private List<FileChange> structuredRepairScope(SubtaskExecutionReport report) {
        if (report == null || report.effectiveChanges() == null || report.effectiveChanges().isEmpty()) {
            return List.of();
        }
        return report.effectiveChanges().stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .toList();
    }

    private ReviewResult withOverrideChanges(ReviewResult review, List<FileChange> overrideChanges) {
        if (review == null) {
            return null;
        }
        return new ReviewResult(
                review.decision(),
                review.fixMode(),
                review.summary(),
                review.changeRequest(),
                review.evidence(),
                review.actionItems(),
                review.implementationPatchTarget(),
                overrideChanges,
                review.revisionRoute(),
                review.reasonCode()
        );
    }

    private ContinuationDisposition incompletePlanContinuation(List<String> incompleteSubtasks) {
        String evidence = (incompleteSubtasks == null || incompleteSubtasks.isEmpty())
                ? "当前实现计划仍有未执行或未完成的子任务。"
                : "未完成子任务：" + String.join("；", incompleteSubtasks);
        return new ContinuationDisposition(
                ImplementationContinuationMode.MID_PLAN_CONTINUE,
                "实现计划尚未执行完毕，当前仍处于阶段中间态。",
                "请继续完成未完成的 implementation 子任务，补齐骨架后的真实行为实现，再重新进入 implementation review。",
                evidence,
                "1. 继续执行未完成的实现子任务。 2. 补齐当前阶段计划中的缺失能力。 3. 仅在所有计划子任务完成后再提交 implementation 审阅。",
                List.of(),
                ImplementationPatchTarget.NONE,
                ReviewReasonCode.NONE
        );
    }

    private ContinuationDisposition contractGateContinuation(
            List<SubtaskExecutionReport> reports,
            ArchitectIntegrationCheckResult contractGateResult,
            List<String> incompleteSubtasks
    ) {
        if (contractGateResult.implementationPatchTarget() == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            HtmlRuntimeOwnershipContract runtimeContract = contractGateResult.runtimeContract();
            if (hasResolvedRuntimeContract(contractGateResult)
                    && completedPlanPatchOwnerResolver.hasResolvableRuntimeWiringOwner(reports, runtimeContract)) {
                return new ContinuationDisposition(
                        ImplementationContinuationMode.PATCH_CONTINUE,
                        ImplementationContractGateMessages.summary(contractGateResult),
                        ImplementationContractGateMessages.changeRequest(contractGateResult),
                        ImplementationContractGateMessages.evidence(contractGateResult, incompleteSubtasks),
                        ImplementationContractGateMessages.actionItems(contractGateResult),
                        runtimeWiringRetryChangeFactory.build(runtimeContract),
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                        ImplementationContractGateMessages.reasonCode(contractGateResult)
                );
            }
            return new ContinuationDisposition(
                    ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK,
                    "当前实现需要继续修复 runtime wiring，但没有可恢复到唯一 completed owner 的结构化接线范围，不能自动续跑。",
                    "请先确认宿主 HTML、companion runtime roots 与唯一 completed owner，再恢复 implementation 续跑。",
                    "runtimeHtmlEntry=" + (runtimeContract == null || runtimeContract.htmlEntryPath() == null
                            ? "(none)"
                            : runtimeContract.htmlEntryPath().toString().replace('\\', '/'))
                            + "\n"
                            + "runtimeRoots=" + (runtimeContract == null || runtimeContract.runtimePaths().isEmpty()
                                    ? "(none)"
                                    : runtimeContract.runtimePaths().stream()
                                            .map(path -> path.toString().replace('\\', '/'))
                                            .reduce((left, right) -> left + "," + right)
                                            .orElse("(none)")),
                    "1. 明确当前需要修复的宿主 HTML。 2. 明确已存在的 companion runtime 根脚本。 3. 明确这些路径能唯一映射到 completed owner。 4. 结构化范围补齐后再恢复自动续跑。",
                    List.of(),
                    ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                    ReviewReasonCode.RUNTIME_WIRING_GAP
            );
        }
        if (contractGateResult.implementationPatchTarget() == ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION) {
            return new ContinuationDisposition(
                    ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK,
                    "当前实现需要继续 patch，但阶段汇总没有拿到结构化文件范围，不能自动续跑。",
                    "请先补齐 overrideChanges 指向的受影响文件，确认修复范围后再继续 implementation。",
                    ImplementationContractGateMessages.evidence(contractGateResult, incompleteSubtasks),
                    "1. 明确当前需要 patch 的文件范围。 2. 确认这些文件仍归当前实现阶段负责。 3. 结构化范围补齐后再恢复自动续跑。",
                    List.of(),
                    ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                    ReviewReasonCode.IMPLEMENTATION_GAP
            );
        }
        return new ContinuationDisposition(
                ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK,
                "当前实现未通过 contract gate，但没有拿到确定性的 patch 目标，不能自动续跑。",
                "请先明确当前 contract gap 的唯一修复目标，再恢复 implementation 续跑。",
                ImplementationContractGateMessages.evidence(contractGateResult, incompleteSubtasks),
                "1. 明确当前 contract gap 的唯一修复目标。 2. 结构化补齐修复范围。 3. 再恢复 implementation 续跑。",
                List.of(),
                ImplementationPatchTarget.NONE,
                ImplementationContractGateMessages.reasonCode(contractGateResult)
        );
    }

    private ContinuationDisposition continuationDisposition(
            SubtaskExecutionReport report,
            ReviewResult review,
            ImplementationContinuationMode mode,
            String summary
    ) {
        String subtaskTitle = report.subtask() == null ? "" : report.subtask().title();
        String evidencePrefix = subtaskTitle == null || subtaskTitle.isBlank()
                ? ""
                : "continuationSubtask=" + subtaskTitle + "\n";
        String normalizedEvidence = blank(review.evidence()).isBlank()
                ? evidencePrefix + "latestReview=" + review.decision().name() + "/" + review.fixMode().name()
                : evidencePrefix + blank(review.evidence());
        return new ContinuationDisposition(
                mode,
                summary,
                blank(review.changeRequest()).isBlank()
                        ? defaultContinuationChangeRequest(mode, review.implementationPatchTarget())
                        : review.changeRequest(),
                normalizedEvidence,
                blank(review.actionItems()).isBlank()
                        ? defaultContinuationActionItems(mode, review.implementationPatchTarget())
                        : review.actionItems(),
                review.overrideChanges(),
                review.implementationPatchTarget(),
                review.reasonCode()
        );
    }

    private String ownershipEvidence(
            ContinuationDisposition continuationDisposition,
            ArchitectIntegrationCheckResult contractGateResult
    ) {
        if (continuationDisposition.implementationPatchTarget() == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            HtmlRuntimeOwnershipContract runtimeContract = contractGateResult == null ? null : contractGateResult.runtimeContract();
            String htmlEntry = runtimeContract == null || runtimeContract.htmlEntryPath() == null
                    ? "(none)"
                    : runtimeContract.htmlEntryPath().toString().replace('\\', '/');
            String runtimeRoots = runtimeContract == null || runtimeContract.runtimePaths().isEmpty()
                    ? "(none)"
                    : runtimeContract.runtimePaths().stream()
                            .map(path -> path.toString().replace('\\', '/'))
                            .reduce((left, right) -> left + "," + right)
                            .orElse("(none)");
            return "runtimeHtmlEntry=" + htmlEntry + "\nruntimeRoots=" + runtimeRoots;
        }
        return continuationDisposition.overrideChanges().isEmpty()
                ? "continuationOverrideChanges=(none)"
                : "continuationOverrideChanges="
                + continuationDisposition.overrideChanges().stream()
                        .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                        .map(FileChange::path)
                        .distinct()
                        .reduce((left, right) -> left + "," + right)
                        .orElse("(none)");
    }

    private ReviewResult missingScopeReview(ReviewResult review) {
        return new ReviewResult(
                review.decision(),
                review.fixMode(),
                "当前实现需要继续 patch，但没有结构化文件范围，不能自动续跑。",
                "请先补齐 overrideChanges 指向的受影响文件，确认修复范围后再继续 implementation。",
                blank(review.evidence()),
                "1. 明确当前需要 patch 的文件范围。 2. 确认这些文件仍归当前实现阶段负责。 3. 结构化范围补齐后再恢复自动续跑。",
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                List.of(),
                ReviewRevisionRoute.REQUEST_HUMAN,
                review.reasonCode() == null ? ReviewReasonCode.IMPLEMENTATION_GAP : review.reasonCode()
        );
    }

    private ReviewResult missingRuntimeContractReview(ReviewResult review) {
        return new ReviewResult(
                review.decision(),
                review.fixMode(),
                "当前实现需要继续修复 runtime wiring，但没有 resolved runtime contract，不能自动续跑。",
                "请先确认宿主 HTML、runtime ownership 与 companion runtime 根脚本，再恢复 implementation 续跑。",
                blank(review.evidence()),
                "1. 明确当前宿主 HTML 入口。 2. 明确当前 runtime ownership。 3. 明确 companion runtime 根脚本集合。 4. 结构化 contract 落盘后再恢复自动续跑。",
                ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                List.of(),
                ReviewRevisionRoute.REQUEST_HUMAN,
                review.reasonCode() == null ? ReviewReasonCode.RUNTIME_WIRING_GAP : review.reasonCode()
        );
    }

    private String defaultContinuationChangeRequest(
            ImplementationContinuationMode mode,
            ImplementationPatchTarget implementationPatchTarget
    ) {
        if (mode == ImplementationContinuationMode.PATCH_CONTINUE) {
            if (implementationPatchTarget == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
                return "请继续按 canonical runtime wiring patch package 修复入口接线，再重新进入 implementation review。";
            }
            if (implementationPatchTarget == ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION) {
                return "请继续按当前 canonical patch package 修补实现缺口，再重新进入 implementation review。";
            }
        }
        if (mode == ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK) {
            return "请先按当前评审结论明确修复范围或补齐阻塞条件，再恢复 implementation 续跑。";
        }
        return "请继续按当前阶段要求补齐实现，再重新进入 implementation review。";
    }

    private String defaultContinuationActionItems(
            ImplementationContinuationMode mode,
            ImplementationPatchTarget implementationPatchTarget
    ) {
        if (mode == ImplementationContinuationMode.BLOCKED_EXHAUSTED_SUBTASK) {
            return "1. 明确当前阻塞点。 2. 补齐结构化修复范围或确认人工处理。 3. 条件满足后再恢复 implementation。";
        }
        if (mode == ImplementationContinuationMode.PATCH_CONTINUE) {
            if (implementationPatchTarget == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
                return "1. 只按 canonical runtime wiring patch package 修复宿主入口与 runtime 根脚本的接线。 2. 保持既有 runtime 所有权不变。 3. 完成后重新验证。";
            }
            if (implementationPatchTarget == ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION) {
                return "1. 只按 canonical patch package 修当前实现缺口。 2. 不要重开新骨架。 3. 完成后重新验证。";
            }
        }
        return "1. 继续修补当前阶段实现。 2. 完成后重新验证。";
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private record ContinuationDisposition(
            ImplementationContinuationMode mode,
            String summary,
            String changeRequest,
            String evidence,
            String actionItems,
            List<FileChange> overrideChanges,
            ImplementationPatchTarget implementationPatchTarget,
            ReviewReasonCode reasonCode
    ) {
    }
}
