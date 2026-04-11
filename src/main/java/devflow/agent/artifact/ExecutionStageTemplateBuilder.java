package devflow.agent.artifact;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;

/**
 * 执行类阶段模板构造器。
 *
 * <p>负责 `IMPLEMENTATION / CODE_REVIEW / TEST` 三个阶段的模板，
 * 让 `ArtifactTemplateFactory` 只保留路由职责。
 */
final class ExecutionStageTemplateBuilder {

    private final ArtifactTemplateSupport support;

    ExecutionStageTemplateBuilder(ArtifactTemplateSupport support) {
        this.support = support;
    }

    String create(StageType stageType, RunRecord runRecord, String note, DocumentLanguage language) {
        return switch (stageType) {
            case IMPLEMENTATION -> buildImplementation(runRecord, note, language);
            case CODE_REVIEW -> buildCodeReview(runRecord, note, language);
            case TEST -> buildTest(runRecord, note, language);
            default -> throw new IllegalArgumentException("Unsupported execution stage: " + stageType);
        };
    }

    private String buildImplementation(RunRecord runRecord, String note, DocumentLanguage language) {
        String generatedAtLabel = support.generatedAtLabel(language);
        return language.isChinese() ? """
                # 代码实现

                - runId: %s
                - 来源阶段: DESIGN
                - %s: %s

                ## 变更目标

                [TODO]

                ## 实施记录

                [TODO]
                """.formatted(runRecord.runId(), generatedAtLabel, support.now()) : """
                # Implementation

                - runId: %s
                - Source Stage: DESIGN
                - %s: %s

                ## Change Objectives

                [TODO]

                ## Execution Log

                [TODO]
                """.formatted(runRecord.runId(), generatedAtLabel, support.now());
    }

    private String buildCodeReview(RunRecord runRecord, String note, DocumentLanguage language) {
        String generatedAtLabel = support.generatedAtLabel(language);
        return language.isChinese() ? """
                # Code Review

                - runId: %s
                - 来源阶段: IMPLEMENTATION
                - %s: %s

                ## 审阅结论

                [TODO]

                ## 问题列表

                [TODO]
                """.formatted(runRecord.runId(), generatedAtLabel, support.now()) : """
                # Code Review

                - runId: %s
                - Source Stage: IMPLEMENTATION
                - %s: %s

                ## Review Conclusion

                [TODO]

                ## Findings

                [TODO]
                """.formatted(runRecord.runId(), generatedAtLabel, support.now());
    }

    private String buildTest(RunRecord runRecord, String note, DocumentLanguage language) {
        String generatedAtLabel = support.generatedAtLabel(language);
        return language.isChinese() ? """
                # 测试报告

                - runId: %s
                - 来源阶段: CODE_REVIEW
                - %s: %s

                ## 测试计划

                [TODO]

                ## 测试结果

                [TODO]
                """.formatted(runRecord.runId(), generatedAtLabel, support.now()) : """
                # Test Report

                - runId: %s
                - Source Stage: CODE_REVIEW
                - %s: %s

                ## Test Plan

                [TODO]

                ## Test Results

                [TODO]
                """.formatted(runRecord.runId(), generatedAtLabel, support.now());
    }
}
