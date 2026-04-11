package devflow.agent.artifact;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;

/**
 * 分析文档模板 builder。
 *
 * <p>负责 `ANALYSIS` 初始模板，避免模板工厂继续堆叠三份长模板。
 */
final class AnalysisDocumentTemplateBuilder {

    private final ArtifactTemplateSupport support;

    AnalysisDocumentTemplateBuilder(ArtifactTemplateSupport support) {
        this.support = support;
    }

    String build(RunRecord runRecord, String note, DocumentLanguage language) {
        String generatedAtLabel = support.generatedAtLabel(language);
        return language.isChinese() ? """
                # 需求分析与调研

                - runId: %s
                - 目标: %s
                - 约束: %s
                - %s: %s

                ## 1. 背景与问题定义

                ### 1.1 背景

                ### 1.2 当前问题

                ### 1.3 为什么现在要做

                ## 2. 目标与成功标准

                ### 2.1 业务目标

                ### 2.2 用户价值

                ### 2.3 成功标准

                ## 3. 关键约束

                ### 3.1 技术约束

                ### 3.2 业务约束

                ### 3.3 交付约束

                ## 4. 初步调研与假设

                ### 4.1 同类方案或参考实现

                ### 4.2 当前假设

                ### 4.3 需要验证的问题

                ## 5. 边界与非目标

                ### 5.1 本轮范围

                ### 5.2 明确不做

                ## 6. 风险与待确认问题

                ### 6.1 主要风险

                ### 6.2 待确认问题

                %s

                %s
                """.formatted(
                runRecord.runId(),
                runRecord.goal(),
                runRecord.constraints(),
                generatedAtLabel,
                support.now(),
                support.numberedSourceMetadataHeading(7, language),
                support.sourceMetadataTemplateBlock()
        ) : """
                # Requirements Analysis And Research

                - runId: %s
                - Goal: %s
                - Constraints: %s
                - %s: %s

                ## 1. Background And Problem Definition

                ### 1.1 Background

                ### 1.2 Current Problem

                ### 1.3 Why Now

                ## 2. Goals And Success Criteria

                ### 2.1 Product Goals

                ### 2.2 User Value

                ### 2.3 Success Criteria

                ## 3. Key Constraints

                ### 3.1 Technical Constraints

                ### 3.2 Business Constraints

                ### 3.3 Delivery Constraints

                ## 4. Initial Research And Hypotheses

                ### 4.1 Comparable Solutions Or References

                ### 4.2 Current Hypotheses

                ### 4.3 Questions To Validate

                ## 5. Boundaries And Non-Goals

                ### 5.1 In Scope

                ### 5.2 Explicitly Out Of Scope

                ## 6. Risks And Open Questions

                ### 6.1 Major Risks

                ### 6.2 Open Questions

                %s

                %s
                """.formatted(
                runRecord.runId(),
                runRecord.goal(),
                runRecord.constraints(),
                generatedAtLabel,
                support.now(),
                support.numberedSourceMetadataHeading(7, language),
                support.sourceMetadataTemplateBlock()
        );
    }
}
