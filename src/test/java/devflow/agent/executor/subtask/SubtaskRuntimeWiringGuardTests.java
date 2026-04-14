package devflow.agent.executor.subtask;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.editing.FileEditScope;
import devflow.agent.executor.runtime.RuntimeOwnershipMode;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewRevisionRoute;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SubtaskRuntimeWiringGuardTests {

    @TempDir
    Path tempDir;

    @Test
    void runtimeWiringGuardUsesCanonicalRetryChangeFactoryForSubtaskRepair() throws Exception {
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("index.html"), """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head><meta charset="UTF-8"><title>Tetris</title></head>
                <body>
                  <main id="app-root"></main>
                  <script>
                    console.log('boot');
                  </script>
                </body>
                </html>
                """);
        Files.writeString(projectDir.resolve("index.app.js"), """
                export function startGame() {
                  return 'started';
                }
                """);

        Subtask subtask = new Subtask(
                "修接线",
                "修复宿主 HTML 与 companion runtime 接线",
                List.of(),
                List.of(),
                List.of(),
                List.of("入口接线正确"),
                false,
                DeliveryMode.PATCH,
                List.of(
                        new FileChange(
                                "index.html",
                                ChangeAction.WRITE,
                                "修复宿主接线",
                                FileEditScope.HOST_HTML_PATCH,
                                RuntimeOwnershipMode.EXTERNAL_COMPANION,
                                true
                        ),
                        new FileChange("index.app.js", ChangeAction.WRITE, "对齐 companion runtime")
                )
        );

        SubtaskRuntimeWiringGuard guard = new SubtaskRuntimeWiringGuard(
                new FileProjectWorkspace(),
                new TreeSitterSupport()
        );

        SubtaskVerificationOutcome outcome = guard.check(projectDir, subtask, DocumentLanguage.ZH);

        assertNotNull(outcome);
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING, outcome.review().implementationPatchTarget());
        assertEquals(ReviewRevisionRoute.PATCH_CURRENT_STAGE, outcome.review().revisionRoute());
        assertEquals(DeliveryMode.PATCH, outcome.revisionDirective().nextDeliveryMode());
        assertEquals(List.of("index.html", "index.app.js"),
                outcome.revisionDirective().retryChanges().stream().map(FileChange::path).toList());
        assertEquals(RuntimeOwnershipMode.EXTERNAL_COMPANION,
                outcome.revisionDirective().retryChanges().getFirst().runtimeOwnership());
        assertEquals(FileEditScope.HOST_HTML_PATCH,
                outcome.revisionDirective().retryChanges().getFirst().effectiveEditScope());
    }
}
