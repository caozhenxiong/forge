package devflow.agent.executor;

import devflow.agent.quality.QualityPlan;
import java.util.List;

public record TestCasePlan(
        String summary,
        List<TestCaseSpec> cases,
        QualityPlan qualityPlan
) {

    public TestCasePlan(String summary, List<TestCaseSpec> cases) {
        this(summary, cases, QualityPlan.empty());
    }
}
