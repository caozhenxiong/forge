package devflow.agent.context;

import devflow.agent.orchestrator.StageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactContextSanitizerTests {

    @Test
    void projectionKeepsAuthoredRecommendationLines() {
        String artifact = """
                ## 4. Non-Functional Requirements
                - Recommendation: 控制响应时间小于 100ms
                - Recommendation: 建议使用单个 HTML 文件交付
                - Recommendation: 体验应保持流畅
                - 页面可直接打开运行
                """;

        String sanitized = ArtifactContextSanitizer.sanitizeForProjection(
                artifact,
                StageType.PRD,
                """
                实现一个可玩的网页版俄罗斯方块
                需要纯网页版、可直接打开运行、像素风、支持开始/暂停/重开、方向键控制、显示得分和下一个方块预览
                """
        );

        assertTrue(sanitized.contains("100ms"));
        assertTrue(sanitized.contains("单个 HTML 文件"));
        assertTrue(sanitized.contains("体验应保持流畅"));
        assertTrue(sanitized.contains("页面可直接打开运行"));
    }

    @Test
    void promptSanitizerDoesNotGuessRecommendationSemanticsFromBodyText() {
        String artifact = """
                ## 5. Acceptance Criteria
                - [ ] 页面可直接打开运行
                - Recommendation: 每个方块大小为 20x20 像素
                - Recommendation: 控制响应时间小于 100ms
                """;

        String sanitized = ArtifactContextSanitizer.sanitizeForPrompt(
                artifact,
                StageType.PRD,
                """
                实现一个可玩的网页版俄罗斯方块
                需要纯网页版、可直接打开运行、像素风、支持开始/暂停/重开、方向键控制、显示得分和下一个方块预览
                """
        );

        assertTrue(sanitized.contains("页面可直接打开运行"));
        assertTrue(sanitized.contains("20x20"));
        assertTrue(sanitized.contains("100ms"));
    }

    @Test
    void projectionKeepsAuthoredDesignChoiceLines() {
        String artifact = """
                ## 1. 技术目标
                - 设计选择：交付物为单一 HTML 文件
                - 设计选择：所有资源均内联
                - 游戏逻辑在浏览器中运行
                """;

        String sanitized = ArtifactContextSanitizer.sanitizeForProjection(
                artifact,
                StageType.DESIGN,
                """
                实现一个可直接打开运行的纯网页版俄罗斯方块
                不依赖后端服务，不需要构建步骤
                """
        );

        assertTrue(sanitized.contains("单一 HTML 文件"));
        assertTrue(sanitized.contains("所有资源均内联"));
        assertTrue(sanitized.contains("游戏逻辑在浏览器中运行"));
    }
}
