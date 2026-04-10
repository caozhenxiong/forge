package devflow.agent.executor;

import devflow.agent.context.ExecutionContract;
import devflow.agent.validation.ProjectFingerprint;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestToolSelectorTests {

    private final TestToolSelector selector = new TestToolSelector();

    @Test
    void selectsPlaywrightForRunnableWebProject() {
        TestToolSelection selection = selector.select(
                webFingerprint("web-static", "index.html"),
                htmlEntryContract(),
                ArchitectIntegrationCheckResult.success()
        );

        assertEquals(TestExecutionTool.PLAYWRIGHT, selection.tool());
        assertTrue(selection.executable());
        assertTrue(selection.supportsRuntimeSnapshot());
        assertEquals("index.html", selection.runtimeSnapshotEntry());
    }

    @Test
    void returnsArchitectFailureWhenRunnableCheckFails() {
        TestToolSelection selection = selector.select(
                webFingerprint("web-static", "index.html"),
                htmlEntryContract(),
                ArchitectIntegrationCheckResult.failure(ArchitectIntegrationFailureReason.ENTRY_MISSING, "入口文件缺失")
        );

        assertEquals(TestExecutionTool.UNAVAILABLE, selection.tool());
        assertFalse(selection.executable());
        assertEquals(TestToolFailureReason.ENTRY_MISSING, selection.failureReason());
        assertTrue(selection.details().contains("整体可运行检查"));
    }

    @Test
    void reportsMissingHtmlEntryAsUnavailable() {
        TestToolSelection selection = selector.select(
                webFingerprint("web-static", null),
                htmlEntryContract(),
                ArchitectIntegrationCheckResult.success()
        );

        assertEquals(TestExecutionTool.UNAVAILABLE, selection.tool());
        assertEquals(TestToolFailureReason.ENTRY_MISSING, selection.failureReason());
    }

    @Test
    void reportsUnsupportedExecutorWhenNoEntryContractExists() {
        TestToolSelection selection = selector.select(
                webFingerprint("java-maven", null),
                new ExecutionContract(
                        false,
                        null,
                        false,
                        false,
                        List.of()
                ),
                ArchitectIntegrationCheckResult.success()
        );

        assertEquals(TestExecutionTool.UNAVAILABLE, selection.tool());
        assertEquals(TestToolFailureReason.UNSUPPORTED_EXECUTOR, selection.failureReason());
    }

    private ProjectFingerprint webFingerprint(String projectType, String htmlEntry) {
        return new ProjectFingerprint(
                projectType,
                "",
                false,
                false,
                false,
                false,
                htmlEntry != null,
                true,
                false,
                htmlEntry,
                htmlEntry == null ? Set.of("game.js") : Set.of(htmlEntry),
                List.of()
        );
    }

    private ExecutionContract htmlEntryContract() {
        return new ExecutionContract(
                true,
                "html-entry",
                true,
                true,
                List.of("page-opens")
        );
    }
}
