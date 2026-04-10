package devflow.agent.validation;

import devflow.agent.util.ProjectPathSupport;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 根据项目指纹构建验证 capability 候选列表。
 *
 * <p>这一层只负责确定性候选推导，不负责提示词或模型结果净化。
 */
final class ValidationCapabilityCandidateBuilder {

    List<ValidationStep> build(ProjectFingerprint fingerprint) {
        List<ValidationStep> steps = new ArrayList<>();
        if (fingerprint.hasPom()) {
            if (fingerprint.fileNames().stream().anyMatch(ProjectPathSupport::isMavenWrapper)) {
                steps.add(new ValidationStep(ValidationCapability.MAVENW_TEST, "检测到 Maven Wrapper，优先用项目自带测试命令。", true));
            }
            steps.add(new ValidationStep(ValidationCapability.MAVEN_TEST, "检测到 pom.xml，可执行 Maven 测试。", true));
        }
        if (fingerprint.hasGradleWrapper()) {
            steps.add(new ValidationStep(ValidationCapability.GRADLEW_TEST, "检测到 Gradle Wrapper，优先用项目自带测试命令。", true));
        } else if (fingerprint.hasGradleBuild()) {
            steps.add(new ValidationStep(ValidationCapability.GRADLE_TEST, "检测到 Gradle 构建文件，可执行 Gradle 测试。", true));
        }
        if (fingerprint.hasPackageJson()) {
            switch (fingerprint.packageManagerType()) {
                case PNPM -> {
                    steps.add(new ValidationStep(ValidationCapability.PNPM_BUILD, "检测到 pnpm 项目，优先执行 build。", true));
                    steps.add(new ValidationStep(ValidationCapability.PNPM_TEST, "检测到 pnpm 项目，可尝试执行 test。", false));
                }
                case YARN -> {
                    steps.add(new ValidationStep(ValidationCapability.YARN_BUILD, "检测到 yarn 项目，优先执行 build。", true));
                    steps.add(new ValidationStep(ValidationCapability.YARN_TEST, "检测到 yarn 项目，可尝试执行 test。", false));
                }
                case NPM, NONE -> {
                    steps.add(new ValidationStep(ValidationCapability.NPM_BUILD, "检测到 npm 项目，优先执行 build。", true));
                    steps.add(new ValidationStep(ValidationCapability.NPM_TEST, "检测到 npm 项目，可尝试执行 test。", false));
                }
            }
        }
        if (fingerprint.hasHtmlEntry()) {
            steps.add(new ValidationStep(ValidationCapability.WEB_RESOURCE_LINK_CHECK, "检测到网页入口，需要确认本地资源引用完整。", true));
            steps.add(new ValidationStep(ValidationCapability.WEB_RUNTIME_WIRING_CHECK, "检测到网页入口，需要确认 runtime 接线和所有权一致。", true));
            steps.add(new ValidationStep(ValidationCapability.WEB_PLAYWRIGHT_SMOKE, "检测到网页入口，需要用浏览器级 smoke test 验证页面至少可打开。", true));
        }
        if (fingerprint.hasHtmlEntry() || fingerprint.hasJavaScript() || fingerprint.hasTypeScript()) {
            steps.add(new ValidationStep(ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK, "检测到前端脚本，需要做 JavaScript 语法检查。", true));
        }
        return dedupe(steps);
    }

    private List<ValidationStep> dedupe(List<ValidationStep> steps) {
        Set<ValidationCapability> seen = new LinkedHashSet<>();
        List<ValidationStep> result = new ArrayList<>();
        for (ValidationStep step : steps) {
            if (seen.add(step.capability())) {
                result.add(step);
            }
        }
        return result;
    }
}
