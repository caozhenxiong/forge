package devflow.agent.validation;

import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.ToolName;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationExecutorTests {

    @TempDir
    Path tempDir;

    private final ValidationExecutor executor = new ValidationExecutor(new FileProjectWorkspace());

    @Test
    void passesResourceAndInlineScriptChecksForValidHtml() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), """
                <!doctype html>
                <html>
                <head>
                  <link rel="stylesheet" href="./style.css">
                </head>
                <body>
                  <script src="./game.js"></script>
                  <script>
                    const canvas = document.createElement('canvas');
                    document.body.appendChild(canvas);
                  </script>
                </body>
                </html>
                """);
        Files.writeString(tempDir.resolve("style.css"), "body { margin: 0; }");
        Files.writeString(tempDir.resolve("game.js"), "export const ready = true;");

        ValidationPlan plan = new ValidationPlan(
                "验证网页资源和内联脚本。",
                List.of(
                        new ValidationStep(ValidationCapability.WEB_RESOURCE_LINK_CHECK, "检查本地资源引用。", true),
                        new ValidationStep(ValidationCapability.WEB_JAVASCRIPT_SYNTAX_CHECK, "检查内联脚本语法。", true)
                )
        );

        SelfCheckResult result = executor.execute(tempDir, null, plan);

        assertTrue(result.passed());
        assertTrue(result.details().contains("inline script ok"));
    }

    @Test
    void failsWhenReferencedLocalAssetIsMissing() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), """
                <!doctype html>
                <html>
                <head>
                  <link rel="stylesheet" href="./missing.css">
                </head>
                <body>
                  <script src="./missing.js"></script>
                </body>
                </html>
                """);

        ValidationPlan plan = new ValidationPlan(
                "检查网页资源引用。",
                List.of(new ValidationStep(ValidationCapability.WEB_RESOURCE_LINK_CHECK, "检查本地资源引用。", true))
        );

        SelfCheckResult result = executor.execute(tempDir, null, plan);

        assertFalse(result.passed());
        assertTrue(result.details().contains("missing.css"));
        assertTrue(result.details().contains("missing.js"));
    }

    @Test
    void detailedExecutionCarriesStructuredToolResults() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), """
                <!doctype html>
                <html>
                <head>
                  <link rel="stylesheet" href="./missing.css">
                </head>
                </html>
                """);

        ValidationPlan plan = new ValidationPlan(
                "检查网页资源引用。",
                List.of(new ValidationStep(ValidationCapability.WEB_RESOURCE_LINK_CHECK, "检查本地资源引用。", true))
        );

        ValidationExecutionReport report = executor.executeDetailed(tempDir, null, plan);

        assertFalse(report.selfCheckResult().passed());
        assertEquals(1, report.toolResults().size());
        assertEquals(ToolName.RESOURCE_LINK_VERIFY, report.toolResults().getFirst().toolName());
        assertEquals("./missing.css", report.toolResults().getFirst().evidence());
    }

    @Test
    void failsWhenCompanionRuntimeExistsButHtmlEntryDoesNotWireIt() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), """
                <!doctype html>
                <html>
                <body>
                  <main id="app-root"></main>
                  <script id="app-script">
                    console.log('still inline');
                  </script>
                </body>
                </html>
                """);
        Files.writeString(tempDir.resolve("index.app.js"), "export const ready = true;");

        ValidationPlan plan = new ValidationPlan(
                "检查网页 runtime 接线。",
                List.of(new ValidationStep(ValidationCapability.WEB_RUNTIME_WIRING_CHECK, "检查入口与 runtime 接线。", true))
        );

        SelfCheckResult result = executor.execute(tempDir, null, plan);

        assertFalse(result.passed());
        assertTrue(result.details().contains("接入运行时") || result.details().contains("运行脚本"));
    }
}
