package devflow.agent.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.review.ReviewResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationStrategyPlannerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deterministicPlanUsesHeuristicCandidatesWhenNoModelIsAvailable() {
        ValidationStrategyPlanner planner = new ValidationStrategyPlanner(null, objectMapper);

        ValidationPlan plan = planner.plan(webProjectFingerprint());

        assertEquals("基于项目指纹生成确定性自检策略。", plan.summary());
        assertEquals(
                List.of(
                        ValidationCapability.WEB_RESOURCE_LINK_CHECK,
                        ValidationCapability.WEB_RUNTIME_WIRING_CHECK,
                        ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                        ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK
                ),
                plan.steps().stream().map(ValidationStep::capability).toList()
        );
    }

    @Test
    void modelPlanCannotDropRequiredSmokeOrRuntimeChecks() {
        ValidationStrategyPlanner planner = new ValidationStrategyPlanner(new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, java.util.Map<String, Object> options) {
                return """
                        {
                          "summary": "采用已有能力进行验证",
                          "steps": [
                            {
                              "capability": "WEB_RESOURCE_LINK_CHECK",
                              "reason": "先验证静态资源",
                              "required": true
                            },
                            {
                              "capability": "NON_EXISTENT_CAPABILITY",
                              "reason": "应被过滤",
                              "required": true
                            }
                          ]
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, java.util.Map<String, Object> options) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, java.util.Map<String, Object> options, ModelRole role) {
                return generate(systemPrompt, userPrompt, options);
            }
        }, objectMapper);

        ValidationPlan plan = planner.plan(webProjectFingerprint());

        assertEquals("采用已有能力进行验证", plan.summary());
        assertEquals(
                List.of(
                        ValidationCapability.WEB_RESOURCE_LINK_CHECK,
                        ValidationCapability.WEB_RUNTIME_WIRING_CHECK,
                        ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                        ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK
                ),
                plan.steps().stream().map(ValidationStep::capability).toList()
        );
        assertEquals("先验证静态资源", plan.steps().getFirst().reason());
        assertTrue(plan.steps().stream().allMatch(ValidationStep::required));
    }

    private ProjectFingerprint webProjectFingerprint() {
        return new ProjectFingerprint(
                "web",
                "none",
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                "index.html",
                Set.of("index.html", "game.js"),
                List.of("检测到 index.html", "检测到 game.js")
        );
    }
}
