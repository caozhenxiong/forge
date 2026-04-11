package devflow.agent.executor;

import devflow.agent.quality.QualityPlan;
import java.util.List;

public record TestCasePlan(
        String summary,
        List<TestCaseSpec> cases,
        QualityPlan qualityPlan,
        UiRuntimeContract uiRuntimeContract,
        UiRuntimeContractValidation uiRuntimeContractValidation
) {

    public TestCasePlan(String summary, List<TestCaseSpec> cases) {
        this(summary, cases, QualityPlan.empty(), UiRuntimeContract.empty(), UiRuntimeContractValidation.success());
    }

    public TestCasePlan(String summary, List<TestCaseSpec> cases, QualityPlan qualityPlan) {
        this(summary, cases, qualityPlan, UiRuntimeContract.empty(), UiRuntimeContractValidation.success());
    }
}
