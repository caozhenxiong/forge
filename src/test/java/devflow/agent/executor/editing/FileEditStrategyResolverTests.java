package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.editing.CodePreciseEditor;
import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileEditStrategyResolverTests {

    @TempDir
    Path tempDir;

    private final TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
    private final LanguageEditAdapter codeEditAdapter = new TreeSitterCodeEditAdapter(
            new TreeSitterTargetLocator(treeSitterSupport),
            new CodePreciseEditor(treeSitterSupport),
            new CodePatchKernel(
                    new PatchVerifier(
                            treeSitterSupport,
                            new GeneratedContentGate(new devflow.agent.project.FileProjectWorkspace(), treeSitterSupport)
                    )
            )
    );

    @Test
    void existingCodeFileUsesAdapterToAllowPreciseEditing() throws Exception {
        Path relativePath = Path.of("game.py");
        Files.writeString(tempDir.resolve(relativePath), """
                def tick():
                    return 1
                """);

        FileEditStrategyResolver resolver = new FileEditStrategyResolver(
                new HtmlPreciseEditor(treeSitterSupport),
                codeEditAdapter
        );

        assertTrue(resolver.shouldUsePreciseCodeEditing(
                tempDir,
                relativePath,
                DeliveryMode.INCREMENTAL,
                true,
                Files.readString(tempDir.resolve(relativePath))
        ));
    }

    @Test
    void skeletonModeStillBlocksMandatoryLocalCodePath() {
        FileEditStrategyResolver resolver = new FileEditStrategyResolver(
                new HtmlPreciseEditor(treeSitterSupport),
                codeEditAdapter
        );

        assertFalse(resolver.mustUseLocalCodeEditing(tempDir, Path.of("game.js"), DeliveryMode.SKELETON));
    }
}
