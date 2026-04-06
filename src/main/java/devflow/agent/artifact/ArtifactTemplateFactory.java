package devflow.agent.artifact;

import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ArtifactTemplateFactory {

    public String create(StageType stageType, RunRecord runRecord, String note) {
        return switch (stageType) {
            case ANALYSIS -> """
                    # 需求分析与调研

                    - runId: %s
                    - 目标: %s
                    - 约束: %s
                    - 生成时间: %s

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

                    ## 7. 当前备注

                    %s
                    """.formatted(runRecord.runId(), runRecord.goal(), runRecord.constraints(), Instant.now(), note);
            case PRD -> """
                    # 产品需求文档

                    - runId: %s
                    - 来源阶段: ANALYSIS
                    - 目标: %s
                    - 约束: %s
                    - 生成时间: %s

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

                    ## 7. 当前备注

                    %s
                    """.formatted(runRecord.runId(), runRecord.goal(), runRecord.constraints(), Instant.now(), note);
            case DESIGN -> """
                    # 技术方案设计

                    - runId: %s
                    - 来源阶段: PRD
                    - 目标: %s
                    - 约束: %s
                    - 生成时间: %s

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

                    ## 8. 当前备注

                    %s
                    """.formatted(runRecord.runId(), runRecord.goal(), runRecord.constraints(), Instant.now(), note);
            case IMPLEMENTATION -> """
                    # 代码实现

                    - runId: %s
                    - 来源阶段: DESIGN
                    - 生成时间: %s

                    ## 变更目标

                    [TODO]

                    ## 实施记录

                    [TODO]

                    ## 当前备注

                    %s
                    """.formatted(runRecord.runId(), Instant.now(), note);
            case CODE_REVIEW -> """
                    # Code Review

                    - runId: %s
                    - 来源阶段: IMPLEMENTATION
                    - 生成时间: %s

                    ## 审阅结论

                    [TODO]

                    ## 问题列表

                    [TODO]

                    ## 当前备注

                    %s
                    """.formatted(runRecord.runId(), Instant.now(), note);
            case TEST -> """
                    # 测试报告

                    - runId: %s
                    - 来源阶段: CODE_REVIEW
                    - 生成时间: %s

                    ## 测试计划

                    [TODO]

                    ## 测试结果

                    [TODO]

                    ## 当前备注

                    %s
                    """.formatted(runRecord.runId(), Instant.now(), note);
        };
    }
}
