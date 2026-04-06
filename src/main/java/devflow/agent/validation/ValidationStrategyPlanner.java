package devflow.agent.validation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ValidationStrategyPlanner {

    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper;

    public ValidationStrategyPlanner(LlmProvider llmProvider, ObjectMapper objectMapper) {
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
    }

    public ValidationPlan plan(ProjectFingerprint fingerprint) {
        List<ValidationStep> candidates = heuristicCandidates(fingerprint);
        if (llmProvider == null || candidates.isEmpty()) {
            return fallbackPlan(fingerprint, candidates);
        }

        try {
            String response = llmProvider.generate(
                    """
                            你是验证策略规划器。请根据项目特征，从给定 capability 列表中选择一个有序子集作为 self-check 策略。
                            你必须只返回 JSON：
                            {
                              "summary": "一句话总结",
                              "steps": [
                                {
                                  "capability": "枚举值",
                                  "reason": "为什么选择该能力",
                                  "required": true
                                }
                              ]
                            }

                            规则：
                            1. 只能从提供的 capability 候选列表里选
                            2. 优先使用项目已有工具链
                            3. 没有构建链路时，退回通用静态 Web 检查
                            4. 不要发明新工具，不要输出命令行
                            """,
                    """
                            项目特征：
                            %s

                            候选 capability：
                            %s
                            """.formatted(
                            String.join("\n", fingerprint.evidence()),
                            candidates.stream()
                                    .map(step -> "- " + step.capability() + ": " + step.reason())
                                    .toList()
                    ),
                    Map.of("num_predict", 600),
                    ModelRole.VALIDATION_STRATEGY
            );
            PlannedPayload payload = objectMapper.readValue(extractJsonObject(response), PlannedPayload.class);
            List<ValidationStep> plannedSteps = sanitizeSteps(payload, candidates);
            if (!plannedSteps.isEmpty()) {
                return new ValidationPlan(payload.summary(), plannedSteps);
            }
        } catch (Exception ignored) {
        }

        return fallbackPlan(fingerprint, candidates);
    }

    private List<ValidationStep> heuristicCandidates(ProjectFingerprint fingerprint) {
        List<ValidationStep> steps = new ArrayList<>();
        if (fingerprint.hasPom()) {
            if (fingerprint.fileNames().contains("mvnw")) {
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
            switch (fingerprint.packageManager()) {
                case "pnpm" -> {
                    steps.add(new ValidationStep(ValidationCapability.PNPM_BUILD, "检测到 pnpm 项目，优先执行 build。", true));
                    steps.add(new ValidationStep(ValidationCapability.PNPM_TEST, "检测到 pnpm 项目，可尝试执行 test。", false));
                }
                case "yarn" -> {
                    steps.add(new ValidationStep(ValidationCapability.YARN_BUILD, "检测到 yarn 项目，优先执行 build。", true));
                    steps.add(new ValidationStep(ValidationCapability.YARN_TEST, "检测到 yarn 项目，可尝试执行 test。", false));
                }
                default -> {
                    steps.add(new ValidationStep(ValidationCapability.NPM_BUILD, "检测到 npm 项目，优先执行 build。", true));
                    steps.add(new ValidationStep(ValidationCapability.NPM_TEST, "检测到 npm 项目，可尝试执行 test。", false));
                }
            }
        }
        if (fingerprint.hasHtmlEntry()) {
            steps.add(new ValidationStep(ValidationCapability.WEB_RESOURCE_LINK_CHECK, "检测到网页入口，需要确认本地资源引用完整。", true));
            steps.add(new ValidationStep(ValidationCapability.WEB_PLAYWRIGHT_SMOKE, "检测到网页入口，需要用浏览器级 smoke test 验证页面至少可打开。", true));
        }
        if (fingerprint.hasHtmlEntry() || fingerprint.hasJavaScript() || fingerprint.hasTypeScript()) {
            steps.add(new ValidationStep(ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK, "检测到前端脚本，需要做 JavaScript 语法检查。", true));
        }
        return dedupe(steps);
    }

    private ValidationPlan fallbackPlan(ProjectFingerprint fingerprint, List<ValidationStep> candidates) {
        if (!candidates.isEmpty()) {
            return new ValidationPlan("基于项目指纹生成默认自检策略。", candidates);
        }
        return new ValidationPlan(
                "未识别到成熟工具链，退回通用脚本与资源检查。",
                List.of(
                        new ValidationStep(ValidationCapability.WEB_RESOURCE_LINK_CHECK, "默认检查网页资源引用。", true),
                        new ValidationStep(ValidationCapability.WEB_PLAYWRIGHT_SMOKE, "默认执行网页 smoke test。", true),
                        new ValidationStep(ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK, "默认检查 JavaScript 语法。", true)
                )
        );
    }

    private List<ValidationStep> sanitizeSteps(PlannedPayload payload, List<ValidationStep> candidates) {
        Map<ValidationCapability, ValidationStep> candidateMap = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(ValidationStep::capability, step -> step, (left, right) -> left, java.util.LinkedHashMap::new));
        List<ValidationStep> result = new ArrayList<>();
        if (payload.steps() == null) {
            return result;
        }
        for (PlannedStep plannedStep : payload.steps()) {
            if (plannedStep.capability() == null) {
                continue;
            }
            ValidationCapability capability;
            try {
                capability = ValidationCapability.valueOf(plannedStep.capability());
            } catch (IllegalArgumentException exception) {
                continue;
            }
            ValidationStep candidate = candidateMap.get(capability);
            if (candidate == null) {
                continue;
            }
            result.add(new ValidationStep(
                    capability,
                    plannedStep.reason() == null || plannedStep.reason().isBlank() ? candidate.reason() : plannedStep.reason(),
                    plannedStep.required() == null ? candidate.required() : plannedStep.required()
            ));
        }
        return dedupe(result);
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

    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private record PlannedPayload(
            @JsonProperty("summary") String summary,
            @JsonProperty("steps") List<PlannedStep> steps
    ) {
    }

    private record PlannedStep(
            @JsonProperty("capability") String capability,
            @JsonProperty("reason") String reason,
            @JsonProperty("required") Boolean required
    ) {
    }
}
