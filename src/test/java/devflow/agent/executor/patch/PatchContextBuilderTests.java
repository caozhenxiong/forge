package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.precise.CodePreciseEditor;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchContextBuilderTests {

    private final TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
    private final TargetLocator targetLocator = new TreeSitterTargetLocator(treeSitterSupport);
    private final LanguageEditAdapter codeEditAdapter = new TreeSitterCodeEditAdapter(
            targetLocator,
            new CodePreciseEditor(treeSitterSupport),
            new CodePatchKernel(
                    new PatchVerifier(
                            treeSitterSupport,
                            new GeneratedContentGate(new FileProjectWorkspace(), treeSitterSupport)
                    )
            )
    );
    private final PatchContextBuilder patchContextBuilder = new PatchContextBuilder(targetLocator, codeEditAdapter);

    @Test
    void describesCodeTargetsThroughSharedAdapterPath() {
        String summary = patchContextBuilder.describeCodeTargets(
                Path.of("game.py"),
                """
                def tick():
                    return 1
                """
        );

        assertTrue(summary.contains("tick"));
        assertTrue(summary.contains("PYTHON"));
    }

    @Test
    void countsTopLevelSymbolsFromAdapterLocatedTargets() {
        int count = patchContextBuilder.topLevelCodeSymbolCount(
                Path.of("game.js"),
                """
                export function tick() {
                  return 1;
                }

                export function render() {
                  return 2;
                }
                """
        );

        assertEquals(2, count);
    }

    @Test
    void describesInlineStyleTargetsThroughSyntheticStylePath() {
        String summary = patchContextBuilder.describeInlineStyleTargets(
                Path.of("index.html"),
                """
                #app {
                  color: red;
                }
                """
        );

        assertTrue(summary.contains("CSS"));
        assertTrue(summary.contains("#app"));
    }
}
