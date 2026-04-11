package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * completed-plan PATCH continuation 的最小续写规划器。
 *
 * <p>这层只处理已经可以确定成单一修复骨架的 PATCH target：
 * 1. 运行时接线修复；
 * 2. scoped 现有实现补丁。
 *
 * <p>只有 `NONE` 会返回 null。其余 concrete patch target 要么生成唯一续跑骨架，
 * 要么因为缺失显式 contract / override scope 而直接失败；这里不允许静默回退到
 * 无约束的重新规划。
 */
final class ImplementationPatchContinuationPlanner {

    ReusableImplementationState build(
            ImplementationStateSnapshot snapshot,
            List<Subtask> subtasks,
            List<SubtaskExecutionReport> previousReports,
            DocumentLanguage language,
            ImplementationPatchTarget implementationPatchTarget,
            List<FileChange> overrideChanges,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        if (implementationPatchTarget == null || implementationPatchTarget == ImplementationPatchTarget.NONE) {
            return null;
        }
        if (implementationPatchTarget == ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION) {
            if (overrideChanges == null || overrideChanges.isEmpty()) {
                throw new IllegalStateException("PATCH_EXISTING_IMPLEMENTATION continuation requires overrideChanges.");
            }
            Subtask continuationSubtask = new Subtask(
                    language.choose("延续修复现有实现缺口", "Continue patching the existing implementation"),
                    blankIfNull(snapshot.summary()),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(language.choose("指定文件上的实现缺口修复完成", "The scoped implementation patch is completed")),
                    true,
                    DeliveryMode.PATCH,
                    List.copyOf(overrideChanges)
            );
            List<Subtask> resumedPlan = new ArrayList<>(subtasks);
            resumedPlan.add(continuationSubtask);
            return new ReusableImplementationState(
                    new ImplementationPlan(blankIfNull(snapshot.summary()), resumedPlan),
                    previousReports == null ? List.of() : previousReports,
                    null
            );
        }
        HtmlRuntimeOwnershipContract targetRuntimeContract = resolveTargetRuntimeContract(
                implementationPatchTarget,
                runtimeContract
        );
        if (targetRuntimeContract == null || !targetRuntimeContract.active()) {
            throw new IllegalStateException("Runtime wiring continuation requires a resolved runtime contract.");
        }
        List<FileChange> continuationChanges = new ArrayList<>();
        continuationChanges.add(new FileChange(
                targetRuntimeContract.htmlEntryPath().toString(),
                ChangeAction.WRITE,
                htmlEntryReason(language, implementationPatchTarget),
                FileEditScope.HOST_HTML_PATCH,
                targetRuntimeContract.runtimeOwnership(),
                true
        ));
        Subtask continuationSubtask = new Subtask(
                title(language, implementationPatchTarget),
                blankIfNull(snapshot.summary()),
                List.of(),
                List.of(),
                List.of(),
                List.of(language.choose("HTML 入口与 runtime ownership/wiring 契约通过", "The HTML runtime ownership/wiring contract passes")),
                true,
                DeliveryMode.PATCH,
                List.copyOf(continuationChanges)
        );
        List<Subtask> resumedPlan = new ArrayList<>(subtasks);
        resumedPlan.add(continuationSubtask);
        return new ReusableImplementationState(
                new ImplementationPlan(blankIfNull(snapshot.summary()), resumedPlan),
                previousReports == null ? List.of() : previousReports,
                null
        );
    }

    private HtmlRuntimeOwnershipContract resolveTargetRuntimeContract(
            ImplementationPatchTarget implementationPatchTarget,
            HtmlRuntimeOwnershipContract runtimeContract
    ) {
        return runtimeContract == null || !runtimeContract.active() ? null : runtimeContract;
    }

    private String title(DocumentLanguage language, ImplementationPatchTarget implementationPatchTarget) {
        return language.choose("延续修复运行时接线缺口", "Continue repairing runtime wiring");
    }

    private String htmlEntryReason(DocumentLanguage language, ImplementationPatchTarget implementationPatchTarget) {
        return language.choose("修复宿主 HTML 与既有 runtime 根脚本的接线", "Repair the host HTML wiring to the existing runtime roots");
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
