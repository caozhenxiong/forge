package devflow.agent.executor.subtask;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewRevisionRoute;
import devflow.agent.review.ReviewSemantics;
import devflow.agent.review.StructuredReviewResult;
import devflow.agent.review.SubtaskBoundaryReviewPayload;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtaskRepairDirectiveResolverTests {

    private final SubtaskRepairDirectiveResolver resolver = new SubtaskRepairDirectiveResolver();

    @Test
    void boundaryViolationNarrowsRepairScopeToOffendingPaths() {
        Subtask subtask = subtask();
        StructuredReviewResult structuredReview = new StructuredReviewResult(
                new ReviewResult(
                        ReviewDecision.APPROVED,
                        FixMode.NONE,
                        "ok",
                        ""
                ),
                ReviewSemantics.empty(),
                new SubtaskBoundaryReviewPayload(
                        true,
                        true,
                        false,
                        "当前子任务提前实现了后续能力。",
                        "src/app.js 已经落入 gameplay 逻辑。",
                        "移除越界实现。",
                        List.of("src/app.js")
                )
        );
        ReviewResult review = new SubtaskBoundaryGate().enforce(subtask, structuredReview, DocumentLanguage.ZH);

        SubtaskVerificationOutcome outcome = resolver.resolveStructuredPatch(
                subtask,
                review,
                structuredReview,
                DocumentLanguage.ZH
        );

        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION, outcome.review().implementationPatchTarget());
        assertEquals(List.of("src/app.js"), paths(outcome.review().overrideChanges()));
        assertEquals(List.of("src/app.js"), paths(outcome.revisionDirective().retryChanges()));
        assertEquals(DeliveryMode.PATCH, outcome.revisionDirective().nextDeliveryMode());
    }

    @Test
    void boundaryViolationWithoutOffendingPathsRequestsHuman() {
        Subtask subtask = subtask();
        StructuredReviewResult structuredReview = new StructuredReviewResult(
                new ReviewResult(
                        ReviewDecision.APPROVED,
                        FixMode.NONE,
                        "ok",
                        ""
                ),
                ReviewSemantics.empty(),
                new SubtaskBoundaryReviewPayload(
                        true,
                        true,
                        false,
                        "当前子任务提前实现了后续能力。",
                        "src/app.js 已经落入 gameplay 逻辑。",
                        "移除越界实现。",
                        List.of()
                )
        );
        ReviewResult review = new SubtaskBoundaryGate().enforce(subtask, structuredReview, DocumentLanguage.ZH);

        SubtaskVerificationOutcome outcome = resolver.resolveStructuredPatch(
                subtask,
                review,
                structuredReview,
                DocumentLanguage.ZH
        );

        assertEquals(ReviewRevisionRoute.REQUEST_HUMAN, outcome.review().revisionRoute());
        assertEquals(ImplementationPatchTarget.NONE, outcome.review().implementationPatchTarget());
        assertTrue(outcome.review().summary().contains("offendingPaths"));
        assertTrue(outcome.revisionDirective().retryChanges().isEmpty());
    }

    @Test
    void explicitPatchOutsideCurrentSubtaskScopeRequestsHuman() {
        ReviewResult review = new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                "需要修复当前实现",
                "请修复超出当前子任务边界的实现缺口。",
                "src/engine.js 不在当前子任务 effective change-set 中。",
                "",
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                List.of(new FileChange("src/engine.js", ChangeAction.WRITE, "修复越界文件")),
                ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET,
                ReviewReasonCode.IMPLEMENTATION_GAP
        );

        SubtaskVerificationOutcome outcome = resolver.resolveExplicitPatch(
                subtask(),
                review,
                DocumentLanguage.ZH
        );

        assertEquals(ReviewRevisionRoute.REQUEST_HUMAN, outcome.review().revisionRoute());
        assertEquals(ImplementationPatchTarget.NONE, outcome.review().implementationPatchTarget());
        assertTrue(outcome.revisionDirective().retryChanges().isEmpty());
    }

    private List<String> paths(List<FileChange> changes) {
        return changes.stream().map(FileChange::path).toList();
    }

    private Subtask subtask() {
        return new Subtask(
                "创建基础HTML结构与Canvas渲染界面",
                "构建入口页面和渲染脚本",
                List.of(),
                List.of("页面与 Canvas 壳体"),
                List.of("gameplay"),
                List.of("页面可打开"),
                true,
                DeliveryMode.INCREMENTAL,
                List.of(
                        new FileChange("index.html", ChangeAction.WRITE, "创建入口"),
                        new FileChange("src/app.js", ChangeAction.WRITE, "创建渲染脚本")
                )
        );
    }
}
