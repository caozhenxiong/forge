package devflow.agent.context;

import devflow.agent.domain.StageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactContextSanitizerTests {

    @Test
    void projectionDropsExplicitRecommendationLines() {
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

        assertFalse(sanitized.contains("100ms"));
        assertFalse(sanitized.contains("单个 HTML 文件"));
        assertFalse(sanitized.contains("体验应保持流畅"));
        assertTrue(sanitized.contains("页面可直接打开运行"));
    }

    @Test
    void promptSanitizerDropsExplicitRecommendationLinesFromBody() {
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
        assertFalse(sanitized.contains("20x20"));
        assertFalse(sanitized.contains("100ms"));
    }

    @Test
    void projectionDropsExplicitDesignChoiceLines() {
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

        assertFalse(sanitized.contains("单一 HTML 文件"));
        assertFalse(sanitized.contains("所有资源均内联"));
        assertTrue(sanitized.contains("游戏逻辑在浏览器中运行"));
    }

    @Test
    void projectionDropsRuntimeBindingLinesNotBackedByAuthorityCorpus() {
        String artifact = """
                ## 1. Runtime Contract
                - entryKind: html-entry
                - entryPackagingMode: entry-with-local-dependencies
                - runtimeOwnershipMode: companion-owned
                - 页面可直接打开运行
                """;

        String sanitized = ArtifactContextSanitizer.sanitizeForProjection(
                artifact,
                StageType.DESIGN,
                """
                html-entry
                entry-with-local-dependencies
                page-opens
                """
        );

        assertTrue(sanitized.contains("entryKind: html-entry"));
        assertTrue(sanitized.contains("entryPackagingMode: entry-with-local-dependencies"));
        assertFalse(sanitized.contains("runtimeOwnershipMode: companion-owned"));
        assertTrue(sanitized.contains("页面可直接打开运行"));
    }

    @Test
    void projectionKeepsRuntimeBindingLinesBackedByControlledTermsEmbeddedInAuthorityProse() {
        String artifact = """
                ## 1. Runtime Contract
                - entryKind: html-entry
                - entryPackagingMode: entry-with-local-dependencies
                - runtimeOwnershipMode: companion-owned
                - 页面可直接打开运行
                """;

        String sanitized = ArtifactContextSanitizer.sanitizeForProjection(
                artifact,
                StageType.DESIGN,
                """
                入口必须保持 html-entry，并采用 companion-owned 方式交付运行时文件。
                页面应保持 entry-with-local-dependencies 的打包形态，以便直接打开运行。
                """
        );

        assertTrue(sanitized.contains("entryKind: html-entry"));
        assertTrue(sanitized.contains("entryPackagingMode: entry-with-local-dependencies"));
        assertTrue(sanitized.contains("runtimeOwnershipMode: companion-owned"));
        assertTrue(sanitized.contains("页面可直接打开运行"));
    }
}
