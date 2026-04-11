package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.StructureGateEvaluator;
import devflow.agent.quality.StructureGateOutcome;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.util.List;

/**
 * 把“子任务责任域完整性检查”收成统一 gate。
 *
 * <p>第一版不替换现有的 `ImplementationCompletenessCheck`，而是在其之上补一层流程语义：
 * 1. 检测结果是否真的落在当前责任域；
 * 2. 当前是否已经来到最终子任务；
 * 3. 当前子任务是否还有明确的 deferredCapabilities 可以留给后续处理。
 *
 * <p>这样 SubtaskExecutor 不再自己维护“要不要因为完整性问题阻塞当前子任务”的 if/else 逻辑，
 * 而是统一走 gate 输出。
 */
class ImplementationCompletenessGate {

    private final ImplementationCompletenessCheck completenessCheck;
    private final StructureGateEvaluator structureGateEvaluator = new StructureGateEvaluator();

    ImplementationCompletenessGate(ImplementationCompletenessCheck completenessCheck) {
        this.completenessCheck = completenessCheck;
    }

    ImplementationCompletenessGateOutcome evaluate(ImplementationCompletenessGateInput input) {
        if (input == null) {
            return new ImplementationCompletenessGateOutcome(ImplementationCompletenessResult.success(), GateReport.success());
        }
        StructureGateOutcome structureGateOutcome = evaluateStructure(input.qualityPlan());
        if (!structureGateOutcome.passed()) {
            return blockingOutcome(
                    ImplementationCompletenessResult.failure(
                            0,
                            0,
                            List.of(structureGateOutcome.summary()),
                            List.of(structureGateOutcome.evidence(), structureGateOutcome.changeRequest())
                    ),
                    "SUBTASK_STRUCTURE_RISK",
                    structureGateOutcome.summary()
            );
        }
        ImplementationCompletenessResult inspection = completenessCheck.inspectSubtask(
                input.projectPath(),
                input.subtask()
        );
        if (input.finalSubtask()) {
            ImplementationCompletenessResult finalPassInspection = completenessCheck.inspectFiles(
                    input.projectPath(),
                    changedPaths(input.subtask())
            );
            if (!finalPassInspection.passed()) {
                return blockingOutcome(finalPassInspection, "SUBTASK_COMPLETENESS_INCOMPLETE", "当前子任务仍停留在骨架或占位实现阶段，不能视为行为完成。");
            }
        }
        if (inspection.passed()) {
            return new ImplementationCompletenessGateOutcome(inspection, GateReport.success());
        }
        boolean noDeferredCapabilities = input.subtask() == null
                || input.subtask().deferredCapabilities() == null
                || input.subtask().deferredCapabilities().isEmpty();
        boolean shouldBlock = input.finalSubtask() || noDeferredCapabilities;
        if (!shouldBlock) {
            return new ImplementationCompletenessGateOutcome(inspection, GateReport.success());
        }
        return blockingOutcome(inspection, "SUBTASK_COMPLETENESS_INCOMPLETE", "当前子任务仍停留在骨架或占位实现阶段，不能视为行为完成。");
    }

    ReviewResult toBlockingReviewResult(ImplementationCompletenessGateOutcome outcome, DocumentLanguage language) {
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                language.choose(
                        "当前子任务仍停留在骨架或占位实现阶段，不能视为行为完成。",
                        "The current subtask still stops at a shell or placeholder implementation and cannot be treated as behavior-complete."
                ),
                language.choose(
                        "请继续把空实现、TODO、占位逻辑或 no-op 处理补成真实行为，不要只保留可运行壳。",
                        "Replace empty implementations, TODOs, placeholders, or no-op handlers with real behavior instead of leaving only a runnable shell."
                ),
                outcome == null || outcome.inspection() == null ? "" : outcome.inspection().evidenceMarkdown(),
                outcome == null || outcome.inspection() == null ? "" : outcome.inspection().actionItemsMarkdown(),
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION
        );
    }

    private StructureGateOutcome evaluateStructure(QualityPlan qualityPlan) {
        if (qualityPlan == null) {
            return StructureGateOutcome.pass();
        }
        return structureGateEvaluator.evaluate(null, qualityPlan);
    }

    private ImplementationCompletenessGateOutcome blockingOutcome(
            ImplementationCompletenessResult inspection,
            String gateCode,
            String summary
    ) {
        return new ImplementationCompletenessGateOutcome(
                inspection,
                GateReport.failure(
                        summary,
                        List.of(new GateIssue(
                                gateCode,
                                inspection == null ? summary : inspection.summary(),
                                GateFailureDisposition.LOCAL_RETRYABLE
                        ))
                )
        );
    }

    private List<Path> changedPaths(Subtask subtask) {
        if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
            return List.of();
        }
        return subtask.changes().stream()
                .map(FileChange::path)
                .filter(path -> path != null && !path.isBlank())
                .map(Path::of)
                .map(Path::normalize)
                .distinct()
                .toList();
    }
}
