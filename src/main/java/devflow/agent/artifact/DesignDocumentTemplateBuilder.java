package devflow.agent.artifact;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.domain.RunRecord;

/**
 * 技术设计模板 builder。
 *
 * <p>负责 `DESIGN` 初始模板，隔离技术设计文档结构定义。
 */
final class DesignDocumentTemplateBuilder {

    private final ArtifactTemplateSupport support;

    DesignDocumentTemplateBuilder(ArtifactTemplateSupport support) {
        this.support = support;
    }

    String build(RunRecord runRecord, String note, DocumentLanguage language) {
        String generatedAtLabel = support.generatedAtLabel(language);
        return language.isChinese() ? """
                # 技术方案设计

                - runId: %s
                - 来源阶段: PRD
                - 目标: %s
                - 约束: %s
                - %s: %s

                ## 1. 技术目标

                ### 1.1 方案目标

                ### 1.2 设计原则

                ## 2. 系统边界与模块划分

                ### 2.1 系统边界

                ### 2.2 模块拆分

                ### 2.3 关键职责分配

                ## 3. 核心数据模型

                ### 3.1 关键实体

                ### 3.2 状态与约束

                ## 4. 关键流程

                ### 4.1 主流程

                ### 4.2 异常流程

                ### 4.3 状态流转

                ## 5. 接口、页面或命令设计

                ### 5.1 外部接口

                ### 5.2 内部调用或模块协作

                ## 6. 测试与验证策略

                ### 6.1 自检策略

                ### 6.2 测试方法

                ### 6.3 验收信号

                ## 7. 风险与取舍

                ### 7.1 技术风险

                ### 7.2 方案取舍

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
                support.numberedContractMetadataHeading(8, language),
                support.contractMetadataTemplateBlock(),
                support.numberedSourceMetadataHeading(9, language),
                support.sourceMetadataTemplateBlock()
        ) : """
                # Technical Design

                - runId: %s
                - Source Stage: PRD
                - Goal: %s
                - Constraints: %s
                - %s: %s

                ## 1. Technical Goals

                ### 1.1 Solution Goals

                ### 1.2 Design Principles

                ## 2. System Boundaries And Module Split

                ### 2.1 System Boundaries

                ### 2.2 Module Decomposition

                ### 2.3 Responsibility Split

                ## 3. Core Data Model

                ### 3.1 Key Entities

                ### 3.2 States And Constraints

                ## 4. Key Flows

                ### 4.1 Main Flow

                ### 4.2 Exceptional Flows

                ### 4.3 State Transitions

                ## 5. Interface, Page, Or Command Design

                ### 5.1 External Interface

                ### 5.2 Internal Collaboration

                ## 6. Test And Validation Strategy

                ### 6.1 Self-Check Strategy

                ### 6.2 Test Methods

                ### 6.3 Acceptance Signals

                ## 7. Risks And Tradeoffs

                ### 7.1 Technical Risks

                ### 7.2 Tradeoffs

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
                support.numberedContractMetadataHeading(8, language),
                support.contractMetadataTemplateBlock(),
                support.numberedSourceMetadataHeading(9, language),
                support.sourceMetadataTemplateBlock()
        );
    }
}
