package devflow.agent.executor.testing;

import devflow.agent.validation.ProjectInspector;
import devflow.agent.validation.ValidationStrategyPlanner;

record TestPlanningComponents(
        ProjectInspector projectInspector,
        ValidationStrategyPlanner strategyPlanner,
        TestCasePlanner testCasePlanner,
        TestToolSelector testToolSelector
) {
}
