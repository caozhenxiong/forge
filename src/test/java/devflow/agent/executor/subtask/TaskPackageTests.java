package devflow.agent.executor.subtask;

import devflow.agent.context.SharedContextBundle;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.i18n.DocumentLanguage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPackageTests {

    @Test
    void alignToSubtaskKeepsExplicitlyEmptyCapabilitiesInsteadOfFallingBack() {
        TaskPackage taskPackage = new TaskPackage(
                "original title",
                "original goal",
                "PATCH",
                true,
                List.of("index.html", "index.html"),
                List.of("CAP-1"),
                List.of("shell"),
                List.of("gameplay"),
                List.of("页面可打开"),
                List.of(),
                List.of(),
                "targeted context",
                sharedContextBundle()
        );
        Subtask narrowedRetry = new Subtask(
                "retry title",
                "retry goal",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                DeliveryMode.PATCH,
                List.of(new FileChange("index.html", ChangeAction.WRITE, "retry current file only"))
        );

        TaskPackage aligned = taskPackage.alignToSubtask(narrowedRetry);

        assertEquals(List.of(), aligned.ownedCapabilities());
        assertEquals(List.of(), aligned.deferredCapabilities());
        assertEquals(List.of("CAP-1"), aligned.coverageRefs());
        assertEquals(List.of("页面可打开"), aligned.acceptanceCriteria());
        assertEquals(List.of("index.html"), aligned.ownedFiles());
    }

    @Test
    void scopeToFileNarrowsOwnedFilesToCurrentFile() {
        TaskPackage taskPackage = new TaskPackage(
                "title",
                "goal",
                "PATCH",
                true,
                List.of("index.html", "src/app.js"),
                List.of("CAP-1"),
                List.of("shell"),
                List.of("gameplay"),
                List.of("页面可打开"),
                List.of(),
                List.of(),
                "shared context",
                sharedContextBundle()
        );

        TaskPackage scoped = taskPackage.scopeToFile("src/app.js", "current file context");

        assertEquals(List.of("src/app.js"), scoped.ownedFiles());
        assertEquals("current file context", scoped.targetedContext());
        assertEquals(List.of("CAP-1"), scoped.coverageRefs());
        assertEquals(List.of("shell"), scoped.ownedCapabilities());
        assertEquals(List.of("gameplay"), scoped.deferredCapabilities());
    }

    @Test
    void markdownIncludesBoundaryContractReminder() {
        TaskPackage taskPackage = new TaskPackage(
                "title",
                "goal",
                "PATCH",
                true,
                List.of(" index.html ", "index.html"),
                List.of("CAP-1", "CAP-1"),
                List.of("shell", "shell"),
                List.of("gameplay"),
                List.of("页面可打开"),
                List.of(),
                List.of(),
                "targeted context",
                sharedContextBundle()
        );

        String markdown = taskPackage.toMarkdown(DocumentLanguage.ZH);

        assertTrue(markdown.contains("边界契约提醒"));
        assertTrue(markdown.contains("deferredCapabilities 只允许留给后续子任务"));
        assertEquals(1, countOccurrences(markdown, "- index.html"));
        assertEquals(1, countOccurrences(markdown, "- shell"));
    }

    private SharedContextBundle sharedContextBundle() {
        return new SharedContextBundle(
                "goal",
                "constraints",
                null,
                List.of(),
                List.of(),
                List.of(),
                "",
                ""
        );
    }

    private int countOccurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
