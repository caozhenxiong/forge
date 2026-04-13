package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

/**
 * 负责测试用例设计产物的 markdown 渲染。
 */
final class TestCaseArtifactRenderer {

    String render(TestCasePlan plan, devflow.agent.i18n.DocumentLanguage language) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(language.choose("测试用例设计", "Test Case Design")).append("\n\n");
        builder.append("- summary: ").append(blank(plan.summary())).append("\n\n");
        if (plan.qualityPlan() != null && !plan.qualityPlan().capabilityMatrix().isEmpty()) {
            builder.append("## ").append(language.choose("能力矩阵", "Capability Matrix")).append("\n\n");
            builder.append(plan.qualityPlan().capabilityMatrix().toMarkdown(language)).append("\n\n");
        }
        int index = 1;
        for (TestCaseSpec testCase : plan.cases()) {
            builder.append("## ").append(language.choose("用例", "Case")).append(" ").append(index++).append(": ").append(blank(testCase.title())).append("\n\n");
            builder.append("- id: ").append(blank(testCase.id())).append("\n");
            builder.append("- type: ").append(blank(testCase.type())).append("\n");
            builder.append("- required: ").append(testCase.required()).append("\n");
            builder.append("- entry: ").append(blank(testCase.entry())).append("\n");
            builder.append("- preconditions: ").append(blank(testCase.preconditions())).append("\n");
            builder.append("- expected: ").append(blank(testCase.expected())).append("\n\n");
            if (testCase.capabilities() != null && !testCase.capabilities().isEmpty()) {
                builder.append("- capabilities: ")
                        .append(String.join(", ", testCase.capabilities()))
                        .append("\n\n");
            }
            if (testCase.requiresObservationWindow()) {
                builder.append("- observationTargetId: ").append(blank(testCase.observationTargetId())).append("\n");
                builder.append("- observationTrigger: ").append(testCase.observationTrigger()).append("\n");
                builder.append("- observationComparison: ").append(testCase.observationComparison()).append("\n\n");
            }
            builder.append("### ").append(language.choose("步骤", "Steps")).append("\n\n");
            renderSteps(builder, testCase.steps());
            builder.append("\n");
        }
        return builder.toString();
    }

    private void renderSteps(StringBuilder builder, List<TestStepSpec> steps) {
        int stepIndex = 1;
        for (TestStepSpec step : steps) {
            builder.append(stepIndex++)
                    .append(". ")
                    .append(step.action());
            if (!blank(step.selector()).isBlank()) {
                builder.append(" selector=").append(step.selector());
            }
            if (!blank(step.key()).isBlank()) {
                builder.append(" key=").append(step.key());
            }
            if (step.count() != null) {
                builder.append(" count=").append(step.count());
            }
            if (step.ms() != null) {
                builder.append(" ms=").append(step.ms());
            }
            if (!blank(step.text()).isBlank()) {
                builder.append(" text=").append(step.text());
            }
            if (step.semantic() != null) {
                builder.append(" semantic=").append(step.semantic().wireValue());
            }
            if (Boolean.TRUE.equals(step.optional())) {
                builder.append(" optional=true");
            }
            builder.append("\n");
        }
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
