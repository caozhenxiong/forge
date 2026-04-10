package devflow.agent.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 负责净化模型返回的验证计划，并构建确定性基础计划。
 */
final class ValidationPlanSanitizer {

    ValidationPlan buildDeterministicPlan(ProjectFingerprint fingerprint, List<ValidationStep> candidates) {
        if (!candidates.isEmpty()) {
            return new ValidationPlan("基于项目指纹生成确定性自检策略。", candidates);
        }
        return new ValidationPlan(
                "未识别到成熟工具链，使用确定性通用脚本与资源检查。",
                List.of(
                        new ValidationStep(ValidationCapability.WEB_RESOURCE_LINK_CHECK, "检查网页资源引用。", true),
                        new ValidationStep(ValidationCapability.WEB_RUNTIME_WIRING_CHECK, "检查网页 runtime 接线。", true),
                        new ValidationStep(ValidationCapability.WEB_PLAYWRIGHT_SMOKE, "执行网页 smoke test。", true),
                        new ValidationStep(ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK, "检查 JavaScript 语法。", true)
                )
        );
    }

    ValidationPlan sanitize(ValidationPlanningPayload payload, List<ValidationStep> candidates) {
        List<ValidationStep> plannedSteps = sanitizeSteps(payload.steps(), candidates);
        if (plannedSteps.isEmpty()) {
            return null;
        }
        return new ValidationPlan(payload.summary(), plannedSteps);
    }

    private List<ValidationStep> sanitizeSteps(List<ValidationPlannedStep> plannedSteps, List<ValidationStep> candidates) {
        Map<ValidationCapability, ValidationStep> candidateMap = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ValidationStep::capability,
                        step -> step,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
        List<ValidationStep> result = new ArrayList<>();
        if (plannedSteps == null) {
            return result;
        }
        for (ValidationPlannedStep plannedStep : plannedSteps) {
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
}
