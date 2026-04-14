package devflow.agent.executor.patch;

import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNull;

class GeneratedHtmlContentValidatorTests {

    @TempDir
    Path tempDir;

    @Test
    void htmlWithoutRuntimeContractStaysNeutralEvenWhenItReferencesRuntimeScript() {
        GeneratedHtmlContentValidator validator = new GeneratedHtmlContentValidator(
                new GeneratedJavaScriptContentValidator(new FileProjectWorkspace())
        );

        GeneratedContentValidationFailure failure = validator.validate(
                tempDir,
                Path.of("partials/card.html"),
                """
                        <!doctype html>
                        <html>
                        <body>
                          <div class="card"></div>
                          <script type="module" src="../index.app.js"></script>
                        </body>
                        </html>
                        """,
                null,
                List.of(Path.of("index.app.js"))
        );

        assertNull(failure);
    }
}
