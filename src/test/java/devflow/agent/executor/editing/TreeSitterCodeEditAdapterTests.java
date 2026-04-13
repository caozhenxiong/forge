package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.CodePreciseEditor;
import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSitterCodeEditAdapterTests {

    private final TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
    private final TargetLocator targetLocator = new TreeSitterTargetLocator(treeSitterSupport);
    private final CodePatchKernel codePatchKernel = new CodePatchKernel(
            new PatchVerifier(
                    treeSitterSupport,
                    new GeneratedContentGate(
                            new devflow.agent.project.FileProjectWorkspace(),
                            treeSitterSupport
                    )
            )
    );
    private final LanguageEditAdapter adapter = new TreeSitterCodeEditAdapter(
            targetLocator,
            new CodePreciseEditor(treeSitterSupport),
            codePatchKernel
    );

    @Test
    void supportsMultipleTreeSitterCodeLanguages() {
        assertTrue(adapter.supports(Path.of("game.js")));
        assertTrue(adapter.supports(Path.of("game.ts")));
        assertTrue(adapter.supports(Path.of("game.py")));
        assertTrue(adapter.supports(Path.of("Game.java")));
        assertTrue(adapter.supports(Path.of("style.css")));
        assertFalse(adapter.supports(Path.of("index.html")));
    }

    @Test
    void locateTargetsDelegatesToSharedTargetLocator() {
        PatchTargetContext context = adapter.locateTargets(
                Path.of("game.py"),
                """
                def tick():
                    return 1
                """
        );

        assertTrue(context.preciseEditingSupported());
        assertTrue(context.targetNames().contains("tick"));
    }

    @Test
    void locateTargetsSupportsCssRuleSelectors() {
        PatchTargetContext context = adapter.locateTargets(
                Path.of("style.css"),
                """
                #app {
                  color: red;
                }

                .panel {
                  display: flex;
                }
                """
        );

        assertTrue(context.preciseEditingSupported());
        assertTrue(context.targetNames().contains("#app"));
        assertTrue(context.insertableTargetNames().contains(".panel"));
    }

    @Test
    void supportsPreciseEditingUsesSharedLocatorCapabilities() {
        assertTrue(adapter.supportsPreciseEditing(
                Path.of("game.js"),
                """
                export function tick() {
                  return 1;
                }
                """
        ));
        assertFalse(adapter.supportsPreciseEditing(Path.of("game.js"), "const broken ="));
    }

    @Test
    void supportsAppendOnlyEditingCoversNewOrEmptyCodeFiles() {
        assertTrue(adapter.supportsAppendOnlyEditing(Path.of("game.py"), ""));
    }
}
