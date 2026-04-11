package devflow.agent.artifact;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;

/**
 * PRD 模板 builder。
 *
 * <p>负责 `PRD` 初始模板，隔离产品需求文档结构定义。
 */
final class PrdDocumentTemplateBuilder {

    private final ArtifactTemplateSupport support;

    PrdDocumentTemplateBuilder(ArtifactTemplateSupport support) {
        this.support = support;
    }

    String build(RunRecord runRecord, String note, DocumentLanguage language) {
        String generatedAtLabel = support.generatedAtLabel(language);
        return language.isChinese() ? """
                # 产品需求文档

                - runId: %s
                - 来源阶段: ANALYSIS
                - 目标: %s
                - 约束: %s
                - %s: %s

                ## 1. 产品目标

                ### 1.1 产品目标

                ### 1.2 成功指标

                ## 2. 目标用户与使用场景

                ### 2.1 目标用户

                ### 2.2 核心场景

                ### 2.3 典型使用流程

                ## 3. 功能范围

                ### 3.1 核心功能

                ### 3.2 辅助功能

                ### 3.3 异常与边界场景

                ## 4. 非功能要求

                ### 4.1 性能

                ### 4.2 可用性与交互

                ### 4.3 兼容性与部署约束

                ## 5. 验收标准

                ### 5.1 功能验收

                ### 5.2 质量验收

                ## 6. 不做什么

                ### 6.1 本轮不包含

                ### 6.2 后续可扩展方向

                %s

                %s

                %s

                %s
                """.formatted(
                runRecord.runId(),
                runRecord.goal(),
                runRecord.constraints(),
                generatedAtLabel,
                support.now(),
                support.numberedContractMetadataHeading(7, language),
                support.contractMetadataTemplateBlock(),
                support.numberedSourceMetadataHeading(8, language),
                support.sourceMetadataTemplateBlock()
        ) : """
                # Product Requirements Document

                - runId: %s
                - Source Stage: ANALYSIS
                - Goal: %s
                - Constraints: %s
                - %s: %s

                ## 1. Product Goals

                ### 1.1 Product Goals

                ### 1.2 Success Metrics

                ## 2. Target Users And Scenarios

                ### 2.1 Target Users

                ### 2.2 Core Scenarios

                ### 2.3 Typical User Flow

                ## 3. Scope

                ### 3.1 Core Capabilities

                ### 3.2 Supporting Capabilities

                ### 3.3 Edge And Failure Scenarios

                ## 4. Non-Functional Requirements

                ### 4.1 Performance

                ### 4.2 Usability And Interaction

                ### 4.3 Compatibility And Deployment Constraints

                ## 5. Acceptance Criteria

                ### 5.1 Functional Acceptance

                ### 5.2 Quality Acceptance

                ## 6. Out Of Scope

                ### 6.1 Not Included In This Iteration

                ### 6.2 Future Extensions

                %s

                %s

                %s

                %s
                """.formatted(
                runRecord.runId(),
                runRecord.goal(),
                runRecord.constraints(),
                generatedAtLabel,
                support.now(),
                support.numberedContractMetadataHeading(7, language),
                support.contractMetadataTemplateBlock(),
                support.numberedSourceMetadataHeading(8, language),
                support.sourceMetadataTemplateBlock()
        );
    }
}
