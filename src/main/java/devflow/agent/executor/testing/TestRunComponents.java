package devflow.agent.executor.testing;

import devflow.agent.executor.gate.ArchitectIntegrationCheck;
import devflow.agent.validation.ValidationExecutor;

record TestRunComponents(
        ValidationExecutor validationExecutor,
        TestRunner testRunner,
        ArchitectIntegrationCheck architectIntegrationCheck
) {
}
