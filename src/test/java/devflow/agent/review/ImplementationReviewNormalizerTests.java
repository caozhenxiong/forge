package devflow.agent.review;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationReviewNormalizerTests {

    private final ImplementationReviewNormalizer normalizer = new ImplementationReviewNormalizer();

    @Test
    void downgradesUnsupportedPerformanceFailureWithoutMeasurementEvidence() {
        ReviewResult result = normalizer.normalize(
                new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "数独算法存在性能问题，无法满足500ms验收标准",
                        "请优化算法性能。",
                        "未提供测量数据。",
                        "",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        List.of(new FileChange("sudoku-engine.js", ChangeAction.WRITE, "补充性能测量与优化"))
                ),
                """
                ## 8. Contract Metadata
                - validation.performanceMeasurementRequired: true
                - validation.pageLoadMaxMs: 500
                """,
                new ReviewSemantics(
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        false
                )
        );

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION, result.implementationPatchTarget());
        assertTrue(result.summary().contains("技术方案已要求性能测量"));
    }

    @Test
    void fillsMissingEvidenceAndActionItemsForNonPerformanceFindings() {
        ReviewResult result = normalizer.normalize(
                new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "入口接线不完整",
                        "请修复入口接线。",
                        "",
                        "",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "修复入口接线"))
                ),
                ""
        );

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertTrue(result.evidence().contains("请结合实际代码变更"));
        assertTrue(result.actionItems().contains("根据 changeRequest 定位受影响文件和函数"));
    }

    @Test
    void doesNotInferPerformanceSemanticsFromReviewerProseWhenStructuredSemanticsMissing() {
        ReviewResult result = normalizer.normalize(
                new ReviewResult(
                        ReviewDecision.REJECTED,
                        FixMode.REWORK,
                        "数独算法存在性能问题，无法满足500ms验收标准",
                        "请重构算法。",
                        "",
                        ""
                ),
                """
                ## 6. 测试与验证策略
                - 做基础功能验证
                """
        );

        assertEquals(ReviewDecision.REJECTED, result.decision());
        assertEquals(FixMode.REWORK, result.fixMode());
    }
}
