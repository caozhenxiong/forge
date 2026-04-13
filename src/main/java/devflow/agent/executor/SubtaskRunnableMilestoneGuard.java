package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.util.List;

/**
 * 负责 runnable milestone 的确定性放行检查。
 *
 * <p>这层把“当前子任务是否已经满足 execution contract 的可运行约束”收成
 * 独立 guard，避免验证支撑类继续同时承担 gate 判断和 review 编排。
 */
final class SubtaskRunnableMilestoneGuard {

    private final ArchitectIntegrationCheck architectIntegrationCheck;
    private final RuntimeWiringRetryChangeFactory runtimeWiringRetryChangeFactory = new RuntimeWiringRetryChangeFactory();

    SubtaskRunnableMilestoneGuard(ArchitectIntegrationCheck architectIntegrationCheck) {
        this.architectIntegrationCheck = architectIntegrationCheck;
    }

    SubtaskVerificationOutcome check(
            Path projectPath,
            Subtask subtask,
            ContractView contractView,
            DocumentLanguage language
    ) {
        if (!subtask.runnableMilestone() || contractView == null || contractView.executionContract() == null) {
            return null;
        }
        ArchitectIntegrationCheckResult runnableCheck = architectIntegrationCheck.verifyRunnableMilestone(
                projectPath,
                contractView.executionContract()
        );
        if (runnableCheck.passed()) {
            return null;
        }
        ReviewResult review = new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                language.choose("当前可运行里程碑尚未满足执行契约。", "The current runnable milestone does not yet satisfy the execution contract."),
                language.choose("请补齐入口接线、运行时初始化或模块集成，使当前交付物达到可启动、可验证状态。", "Add the missing entry wiring, runtime initialization, or module integration so the deliverable becomes launchable and verifiable."),
                runnableCheck.details(),
                language.choose("优先修复入口接线、模块加载和运行时初始化，不要停留在静态骨架。", "Prioritize entry wiring, module loading, and runtime initialization instead of stopping at a static shell."),
                runnableCheck.implementationPatchTarget() == null
                        ? ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
                        : runnableCheck.implementationPatchTarget()
        );
        return SubtaskVerificationOutcome.of(
                review,
                buildRevisionDirective(subtask, runnableCheck)
        );
    }

    private SubtaskRevisionDirective buildRevisionDirective(
            Subtask subtask,
            ArchitectIntegrationCheckResult runnableCheck
    ) {
        if (runnableCheck == null || runnableCheck.implementationPatchTarget() == null) {
            return SubtaskRevisionDirective.patch(List.of());
        }
        if (runnableCheck.implementationPatchTarget() == ImplementationPatchTarget.PATCH_RUNTIME_WIRING) {
            HtmlRuntimeOwnershipContract runtimeContract = runnableCheck.runtimeContract();
            if (runtimeContract == null || !runtimeContract.active() || runtimeContract.htmlEntryPath() == null) {
                throw new IllegalStateException("Runnable milestone wiring patch requires an explicit runtime contract.");
            }
            return SubtaskRevisionDirective.patch(runtimeWiringRetryChangeFactory.build(runtimeContract));
        }
        if (subtask == null || subtask.changes() == null) {
            return SubtaskRevisionDirective.patch(List.of());
        }
        return SubtaskRevisionDirective.patch(subtask.changes());
    }
}
