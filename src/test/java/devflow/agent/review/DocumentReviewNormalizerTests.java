package devflow.agent.review;

import devflow.agent.context.ContractExtractor;
import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentReviewNormalizerTests {

    private final DocumentReviewNormalizer normalizer = new DocumentReviewNormalizer(new ContractExtractor());

    @Test
    void approvesLowAuthorityClarificationFalsePositive() {
        ReviewResult result = normalizer.normalize(
                dummyRun(),
                StageType.ANALYSIS,
                """
                # 需求分析与调研

                ## 7. Source Metadata
                - hard.userRequirements: 纯前端运行, 需要可直接启动的入口
                - hard.upstreamFacts: (none)
                - soft.inferences: 预览方块可能放在右侧信息栏
                - soft.designDecisions: (none)
                - soft.recommendations: 移动端支持可作为后续增强
                - open.questions: 分数计算规则, 预览方块显示位置, 移动端支持策略
                """,
                new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "当前分析仍缺少若干关键细节。",
                        "请明确分数计算规则、预览方块显示位置和移动端支持策略。",
                        "文档中尚未给出这些细节的最终答案。",
                        "1. 补充分数规则。 2. 明确预览位置。 3. 说明移动端支持。"
                ),
                new ReviewSemantics(
                        true,
                        false,
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false
                )
        );

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void approvesUnsupportedQuantificationHardening() {
        ReviewResult result = normalizer.normalize(
                dummyRun(),
                StageType.PRD,
                """
                # 产品需求文档

                ## 7. Source Metadata
                - hard.userRequirements: 纯前端运行
                - hard.upstreamFacts: (none)
                """,
                new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "文档缺少可量化性能指标。",
                        "请补充页面启动时间小于 2 秒、帧率高于 30 FPS 的明确硬指标。",
                        "",
                        ""
                ),
                new ReviewSemantics(
                        true,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false
                )
        );

        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void doesNotGuessSemanticsFromReviewerProseWhenStructuredSemanticsMissing() {
        ReviewResult result = normalizer.normalize(
                dummyRun(),
                StageType.ANALYSIS,
                """
                # 需求分析与调研

                ## 7. Source Metadata
                - hard.userRequirements: 纯前端运行
                - hard.upstreamFacts: (none)
                """,
                new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "需求分析缺少页面原型和 500ms 性能指标。",
                        "请补充算法细节、交互原型和 500ms 指标。",
                        "",
                        ""
                )
        );

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
    }

    @Test
    void rejectsApprovedDocumentWhenStructuredSemanticsReportUnsupportedQuantitativeConstraint() {
        ReviewResult result = normalizer.normalize(
                dummyRun(),
                StageType.PRD,
                """
                # 产品需求文档

                - 页面加载时间应小于 2 秒
                - 响应延迟应小于 100ms
                """,
                new ReviewResult(
                        ReviewDecision.APPROVED,
                        FixMode.NONE,
                        "整体通过。",
                        ""
                ),
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
                        false,
                        false,
                        true,
                        false
                )
        );

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.decision());
        assertEquals(FixMode.PATCH, result.fixMode());
        assertTrue(result.changeRequest().contains("量化指标"));
        assertTrue(result.actionItems().contains("不要新增新的 hard.* 或 validation.*"));
    }

    @Test
    void rewritesContradictoryActionItemsWhenUnsupportedQuantitativeConstraintIsPresent() {
        ReviewResult result = normalizer.normalize(
                dummyRun(),
                StageType.PRD,
                """
                # 产品需求文档

                ## 4. 非功能要求
                - 页面加载时间不超过 2 秒
                """,
                new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "文档包含无来源量化指标。",
                        "请删除这些量化指标。",
                        "页面加载时间 2 秒没有 authority。",
                        "更新Source Metadata增加hard.performance.pageLoadTime: <2s"
                ),
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
                        false,
                        false,
                        true,
                        false
                )
        );

        assertTrue(result.actionItems().contains("删除候选文档中无来源支撑的量化阈值"));
        assertFalse(result.actionItems().contains("hard.performance.pageLoadTime"));
    }

    private RunRecord dummyRun() {
        Map<StageType, StageExecution> stageStates = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            stageStates.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                null,
                "实现可直接运行的俄罗斯方块网页小游戏",
                "纯前端运行",
                RunConfig.defaultConfig(),
                StageType.ANALYSIS,
                RunStatus.IN_PROGRESS,
                stageStates,
                Instant.now(),
                Instant.now()
        );
    }
}
