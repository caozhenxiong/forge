package devflow.agent.executor;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationRuntimeContractResolverTests {

    private final ImplementationRuntimeContractResolver resolver = new ImplementationRuntimeContractResolver();

    @Test
    void resolvesInlineHostFromCompletedSnapshotWithoutArchitectContract() {
        ImplementationStateSnapshot snapshot = new ImplementationStateSnapshot(
                "建立网页入口",
                List.of(new ImplementationStateSnapshot.PlannedSubtaskState(
                        "建立入口",
                        "创建 index.html",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("入口存在"),
                        true,
                        "PATCH",
                        List.of(new ImplementationStateSnapshot.FileChangeState(
                                "index.html",
                                "WRITE",
                                "补齐宿主入口",
                                FileEditScope.HOST_HTML_PATCH.name(),
                                RuntimeOwnershipMode.INLINE_HOST.name()
                        ))
                )),
                List.of(new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                        "建立入口",
                        true,
                        List.of()
                )),
                List.of(),
                "",
                true,
                true,
                List.of()
        );

        HtmlRuntimeOwnershipContract contract = resolver.resolve(snapshot);

        assertNotNull(contract);
        assertTrue(contract.inlineHost());
        assertEquals(Path.of("index.html"), contract.htmlEntryPath());
        assertTrue(contract.runtimePaths().isEmpty());
    }

    @Test
    void resolvesExternalCompanionFromCompletedRuntimeRootChange() {
        ImplementationStateSnapshot snapshot = new ImplementationStateSnapshot(
                "外提宿主脚本",
                List.of(new ImplementationStateSnapshot.PlannedSubtaskState(
                        "外提运行时",
                        "把内联脚本外提到 companion runtime",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("入口接线完成"),
                        true,
                        "PATCH",
                        List.of(
                                new ImplementationStateSnapshot.FileChangeState(
                                        "index.html",
                                        "WRITE",
                                        "收口宿主入口",
                                        FileEditScope.HOST_HTML_PATCH.name(),
                                        RuntimeOwnershipMode.EXTERNAL_COMPANION.name()
                                ),
                                new ImplementationStateSnapshot.FileChangeState(
                                        "index.app.js",
                                        "WRITE",
                                        "落地 runtime 根脚本"
                                )
                        )
                )),
                List.of(new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                        "外提运行时",
                        true,
                        List.of()
                )),
                List.of(),
                "",
                true,
                true,
                List.of()
        );

        HtmlRuntimeOwnershipContract contract = resolver.resolve(snapshot);

        assertNotNull(contract);
        assertTrue(contract.externalCompanion());
        assertEquals(Path.of("index.html"), contract.htmlEntryPath());
        assertEquals(List.of(Path.of("index.app.js")), contract.runtimePaths());
    }

    @Test
    void prefersReportEffectiveChangesOverOriginalPlanChanges() {
        ImplementationStateSnapshot snapshot = new ImplementationStateSnapshot(
                "修复 runtime wiring",
                List.of(new ImplementationStateSnapshot.PlannedSubtaskState(
                        "补逻辑",
                        "原计划继续改 js 文件",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("入口接线完成"),
                        true,
                        "PATCH",
                        List.of(
                                new ImplementationStateSnapshot.FileChangeState("js/game-engine.js", "WRITE", "旧计划"),
                                new ImplementationStateSnapshot.FileChangeState("js/renderer.js", "WRITE", "旧计划")
                        )
                )),
                List.of(new ImplementationStateSnapshot.SubtaskExecutionStateSnapshot(
                        "补逻辑",
                        true,
                        List.of(),
                        "PATCH",
                        true,
                        List.of(),
                        List.of(new ImplementationStateSnapshot.FileChangeState(
                                "index.html",
                                "WRITE",
                                "改成修宿主接线",
                                FileEditScope.HOST_HTML_PATCH.name(),
                                RuntimeOwnershipMode.EXTERNAL_COMPANION.name(),
                                true
                        ))
                )),
                List.of(),
                "",
                true,
                true,
                List.of()
        );

        HtmlRuntimeOwnershipContract contract = resolver.resolve(snapshot);

        assertNotNull(contract);
        assertTrue(contract.externalCompanion());
        assertEquals(Path.of("index.html"), contract.htmlEntryPath());
        assertTrue(contract.runtimePaths().isEmpty());
    }
}
