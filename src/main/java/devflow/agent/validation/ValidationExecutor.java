package devflow.agent.validation;

import devflow.agent.executor.tools.ToolResult;

import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.testing.PlaywrightExecutionPolicy;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ValidationExecutor {

    private final ValidationCommandSupport commandSupport;
    private final WebResourceValidationSupport resourceValidationSupport;
    private final WebRuntimeWiringValidationSupport runtimeWiringValidationSupport;
    private final JavaScriptSyntaxValidationSupport javaScriptSyntaxValidationSupport;
    private final PlaywrightSmokeValidationSupport playwrightSmokeValidationSupport;

    public ValidationExecutor(FileProjectWorkspace workspace, PlaywrightExecutionPolicy playwrightExecutionPolicy) {
        this.commandSupport = new ValidationCommandSupport(workspace);
        this.resourceValidationSupport = new WebResourceValidationSupport(workspace);
        this.runtimeWiringValidationSupport = new WebRuntimeWiringValidationSupport(workspace);
        this.javaScriptSyntaxValidationSupport = new JavaScriptSyntaxValidationSupport(workspace);
        this.playwrightSmokeValidationSupport = new PlaywrightSmokeValidationSupport(workspace, playwrightExecutionPolicy);
    }

    public SelfCheckResult execute(Path projectPath, ProjectFingerprint fingerprint, ValidationPlan plan) {
        return executeDetailed(projectPath, fingerprint, plan).selfCheckResult();
    }

    public ValidationExecutionReport executeDetailed(Path projectPath, ProjectFingerprint fingerprint, ValidationPlan plan) {
        List<String> detailLines = new ArrayList<>();
        List<ToolResult> toolResults = new ArrayList<>();
        for (ValidationStep step : plan.steps()) {
            ValidationStepExecution execution = runStep(projectPath, fingerprint, step);
            ValidationStepResult result = execution.stepResult();
            toolResults.add(execution.toolResult());
            detailLines.add("""
                    [%s] %s
                    %s
                    """.formatted(result.status(), result.capability(), trim(result.details())));
            if (result.status() == ValidationStatus.FAILED && step.required()) {
                return new ValidationExecutionReport(
                        new SelfCheckResult(false, result.summary(), String.join("\n", detailLines)),
                        List.copyOf(toolResults)
                );
            }
        }
        return new ValidationExecutionReport(
                new SelfCheckResult(true, "项目自测通过。", plan.summary() + "\n\n" + String.join("\n", detailLines)),
                List.copyOf(toolResults)
        );
    }

    private ValidationStepExecution runStep(Path projectPath, ProjectFingerprint fingerprint, ValidationStep step) {
        ValidationCapability capability = step.capability();
        if (capability == ValidationCapability.MAVENW_TEST) {
            return commandSupport.runCommandStep(projectPath, ValidationCapability.MAVENW_TEST, List.of("./mvnw", "-q", "test"), step.reason(), true);
        }
        if (capability == ValidationCapability.MAVEN_TEST) {
            return commandSupport.runCommandStep(projectPath, ValidationCapability.MAVEN_TEST, List.of("mvn", "-q", "test"), step.reason(), true);
        }
        if (capability == ValidationCapability.GRADLEW_TEST) {
            return commandSupport.runCommandStep(projectPath, ValidationCapability.GRADLEW_TEST, List.of("./gradlew", "test"), step.reason(), true);
        }
        if (capability == ValidationCapability.GRADLE_TEST) {
            return commandSupport.runCommandStep(projectPath, ValidationCapability.GRADLE_TEST, List.of("gradle", "test"), step.reason(), true);
        }
        if (capability == ValidationCapability.NPM_BUILD) {
            return commandSupport.runCommandStep(projectPath, ValidationCapability.NPM_BUILD, List.of("npm", "run", "build"), step.reason(), false);
        }
        if (capability == ValidationCapability.NPM_TEST) {
            return commandSupport.runCommandStep(projectPath, ValidationCapability.NPM_TEST, List.of("npm", "run", "test"), step.reason(), false);
        }
        if (capability == ValidationCapability.PNPM_BUILD) {
            return commandSupport.runCommandStep(projectPath, ValidationCapability.PNPM_BUILD, List.of("pnpm", "run", "build"), step.reason(), false);
        }
        if (capability == ValidationCapability.PNPM_TEST) {
            return commandSupport.runCommandStep(projectPath, ValidationCapability.PNPM_TEST, List.of("pnpm", "run", "test"), step.reason(), false);
        }
        if (capability == ValidationCapability.YARN_BUILD) {
            return commandSupport.runCommandStep(projectPath, ValidationCapability.YARN_BUILD, List.of("yarn", "build"), step.reason(), false);
        }
        if (capability == ValidationCapability.YARN_TEST) {
            return commandSupport.runCommandStep(projectPath, ValidationCapability.YARN_TEST, List.of("yarn", "test"), step.reason(), false);
        }
        if (capability == ValidationCapability.WEB_RESOURCE_LINK_CHECK) {
            return resourceValidationSupport.run(projectPath, step.reason());
        }
        if (capability == ValidationCapability.WEB_RUNTIME_WIRING_CHECK) {
            return runtimeWiringValidationSupport.run(projectPath, fingerprint, step.reason());
        }
        if (capability == ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK) {
            return javaScriptSyntaxValidationSupport.run(projectPath, step.reason());
        }
        return playwrightSmokeValidationSupport.run(projectPath, fingerprint, step.reason());
    }

    private String trim(String value) {
        return PlaceholderValues.truncateTail(value, 12000);
    }

}
