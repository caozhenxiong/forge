package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ProductContract;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.markdown.MarkdownSectionScanner;
import devflow.agent.domain.StageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentStagePostProcessorTests {

    private final ContractExtractor contractExtractor = new ContractExtractor();
    private final DocumentStagePostProcessor postProcessor =
            new DocumentStagePostProcessor(contractExtractor, new DocumentDraftAssembler());

    @Test
    void stabilizeContractMetadataKeepsValidationKeysReadableAndParsable() {
        String result = postProcessor.stabilizeContractMetadata(
                """
                # 产品需求文档

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                """,
                new ExecutionContract(true, "html-entry", true, true, java.util.List.of("page-opens")),
                new ValidationMetadata(true, 2000, 50),
                7
        );

        assertTrue(result.contains("validation.performanceMeasurementRequired: true"));
        assertTrue(result.contains("validation.pageLoadMaxMs: 2000"));
        assertTrue(result.contains("validation.interactionMaxMs: 50"));
        assertEquals(new ValidationMetadata(true, 2000, 50), contractExtractor.extractValidationMetadata(result));
    }

    @Test
    void sanitizationRemovesLegacyCurrentNotesFromCanonicalDocument() {
        String sanitized = postProcessor.sanitizeDocumentConstraintEscalation(
                StageType.DESIGN,
                """
                # 技术方案设计

                ## 1. 技术目标
                内容

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, runtime-surface-renders

                ## 9. Source Metadata
                - hard.userRequirements: 纯网页版
                - hard.upstreamFacts: 可直接打开运行

                ## 10. Current Notes
                历史修订说明
                """,
                ConstraintSourceMetadata.empty(),
                ValidationMetadata.empty(),
                DocumentLanguage.ZH
        );

        assertFalse(sanitized.contains("Current Notes"));
        assertFalse(sanitized.contains("历史修订说明"));
    }

    @Test
    void prdSanitizationRemovesUnsourcedQuantitativeThresholdsFromBodyAndDerivedContract() {
        String sanitized = postProcessor.sanitizeDocumentConstraintEscalation(
                StageType.PRD,
                """
                # 产品需求文档

                ## 4. 非功能要求

                ### 4.1 性能
                - 游戏响应流畅，控制无明显延迟
                - 页面加载时间不超过 2 秒
                - 支持主流浏览器运行

                ### 4.2 可用性与交互
                - 支持键盘方向键控制

                ## 5. 验收标准

                ### 5.1 功能验收
                - [ ] 点击“开始”按钮后游戏可正常运行

                ### 5.2 质量验收
                - [ ] 页面加载时间小于 2 秒
                - [ ] 控制响应流畅，无明显卡顿
                - [ ] 游戏在主流浏览器中可正常运行
                """,
                new ConstraintSourceMetadata(
                        java.util.List.of("纯网页版、可直接打开运行"),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of()
                ),
                ValidationMetadata.empty(),
                DocumentLanguage.ZH
        );

        ProductContract productContract = contractExtractor.projectProductContractFromPrd(sanitized);

        assertFalse(sanitized.contains("2 秒"));
        assertTrue(sanitized.contains("控制无明显延迟"));
        assertTrue(sanitized.contains("主流浏览器"));
        assertTrue(productContract.nonFunctionalRequirements().stream().noneMatch(item -> item.contains("2 秒")));
        assertTrue(productContract.acceptanceCriteria().stream().noneMatch(item -> item.contains("2 秒")));
    }

    @Test
    void prdSanitizationProjectsValidationBackIntoCanonicalThresholdLine() {
        String sanitized = postProcessor.sanitizeDocumentConstraintEscalation(
                StageType.PRD,
                """
                # 产品需求文档

                ## 4. 非功能要求

                ### 4.1 性能
                - 页面加载时间小于 2 秒
                - 支持主流浏览器运行
                """,
                new ConstraintSourceMetadata(
                        java.util.List.of("纯网页版、可直接打开运行"),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of()
                ),
                new ValidationMetadata(true, 2000, null),
                DocumentLanguage.ZH
        );

        assertTrue(sanitized.contains("页面加载时间不超过 2 秒"));
        assertFalse(sanitized.contains("页面加载时间小于 2 秒"));
    }

    @Test
    void prdSanitizationPreservesTopLevelSectionBoundariesWhenRewritingSections() {
        String sanitized = postProcessor.sanitizeDocumentConstraintEscalation(
                StageType.PRD,
                """
                # 产品需求文档

                ## 4. 非功能要求
                保持实现简单且可测试。

                ## 5. 验收标准
                能创建 run 并推进到 code review。

                ## 6. 不做什么
                不做完整 web UI。
                """,
                new ConstraintSourceMetadata(
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of()
                ),
                ValidationMetadata.empty(),
                DocumentLanguage.ZH
        );

        assertTrue(sanitized.contains("保持实现简单且可测试。\n\n## 5. 验收标准"));
        assertTrue(sanitized.contains("能创建 run 并推进到 code review。\n\n## 6. 不做什么"));
        assertEquals(
                java.util.List.of(4, 5, 6),
                MarkdownSectionScanner.scanSecondLevelSections(sanitized).stream()
                        .map(MarkdownSectionScanner.Section::number)
                        .toList()
        );
    }

    @Test
    void prdSanitizationRoutesExplicitLowAuthorityItemsToSourceMetadataOnly() {
        String sanitized = postProcessor.sanitizeDocumentConstraintEscalation(
                StageType.PRD,
                """
                # 产品需求文档

                ## 3. 功能范围

                ### 3.1 核心功能
                - 支持开始、暂停、重新开始

                ### 3.2 可选增强
                - 推断：支持移动端触摸操作
                - 建议：添加简单的音效反馈
                - 支持不同难度等级设置

                ## 4. 非功能要求

                ### 4.2 可用性与交互
                - 设计选择：默认自动开始

                ## 5. 验收标准

                ### 5.1 功能验收
                - 待确认问题：是否需要首版支持触屏手势控制？

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens

                ## 8. Source Metadata
                - hard.userRequirements: 实现一个可玩的网页版俄罗斯方块
                - hard.upstreamFacts: 做成精致的网页版
                - soft.inferences: (none)
                - soft.designDecisions: (none)
                - soft.recommendations: (none)
                - open.questions: (none)
                """,
                new ConstraintSourceMetadata(
                        java.util.List.of("实现一个可玩的网页版俄罗斯方块"),
                        java.util.List.of("做成精致的网页版"),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of()
                ),
                ValidationMetadata.empty(),
                DocumentLanguage.ZH
        );

        ProductContract productContract = contractExtractor.projectProductContractFromPrd(sanitized);
        ConstraintSourceMetadata metadata = contractExtractor.extractConstraintSourceMetadata(sanitized);

        assertFalse(sanitized.contains("推断：支持移动端触摸操作"));
        assertFalse(sanitized.contains("建议：添加简单的音效反馈"));
        assertFalse(sanitized.contains("设计选择：默认自动开始"));
        assertFalse(sanitized.contains("待确认问题：是否需要首版支持触屏手势控制？"));
        assertTrue(productContract.optionalCapabilities().contains("支持不同难度等级设置"));
        assertFalse(productContract.optionalCapabilities().contains("推断：支持移动端触摸操作"));
        assertTrue(metadata.softInferences().contains("支持移动端触摸操作"));
        assertTrue(metadata.softRecommendations().contains("添加简单的音效反馈"));
        assertTrue(metadata.softDesignDecisions().contains("默认自动开始"));
        assertTrue(metadata.openQuestions().contains("是否需要首版支持触屏手势控制"));
    }
}
