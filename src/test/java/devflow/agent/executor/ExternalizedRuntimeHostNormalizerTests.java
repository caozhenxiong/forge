package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalizedRuntimeHostNormalizerTests {

    private final ExternalizedRuntimeHostNormalizer normalizer = new ExternalizedRuntimeHostNormalizer();

    @Test
    void removesInlineAppScriptWhenCompanionRuntimeIsAlreadyReferenced() {
        String normalized = normalizer.normalize(
                Path.of("index.html"),
                HtmlRuntimeOwnershipContract.externalCompanion(Path.of("index.html"), java.util.List.of(Path.of("index.app.js"))),
                """
                        <!DOCTYPE html>
                        <html>
                        <head><title>demo</title></head>
                        <body>
                          <main id="app-root"></main>
                          <script id="app-script">
                            console.log('inline runtime');
                          </script>
                          <script src="./index.app.js"></script>
                        </body>
                        </html>
                        """
        );

        assertFalse(normalized.contains("id=\"app-script\""));
        assertTrue(normalized.contains("<script src=\"./index.app.js\"></script>"));
    }

    @Test
    void leavesHtmlUntouchedWhenCompanionRuntimeIsNotReferenced() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head><title>demo</title></head>
                <body>
                  <script id="app-script">console.log('inline runtime');</script>
                </body>
                </html>
                """;

        String normalized = normalizer.normalize(Path.of("index.html"), null, html);

        assertTrue(normalized.contains("id=\"app-script\""));
    }

    @Test
    void deduplicatesRuntimeReferencesAndDuplicateIds() {
        String normalized = normalizer.normalize(
                Path.of("main.html"),
                HtmlRuntimeOwnershipContract.externalCompanion(Path.of("main.html"), java.util.List.of(Path.of("main.app.js"))),
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                          <meta charset="UTF-8">
                          <meta charset="UTF-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1.0">
                          <meta name="viewport" content="width=device-width, initial-scale=1.0">
                          <title>demo</title>
                          <title>demo</title>
                          <script src="main.app.js"></script>
                          <script src="main.app.js"></script>
                        </head>
                        <body>
                          <canvas id="game-canvas"></canvas>
                          <canvas id="game-canvas"></canvas>
                        </body>
                        </html>
                        """
        );

        assertEquals(1, countOccurrences(normalized, "<meta charset=\"UTF-8\">"));
        assertEquals(1, countOccurrences(normalized, "meta name=\"viewport\""));
        assertEquals(1, countOccurrences(normalized, "<title>demo</title>"));
        assertEquals(1, countOccurrences(normalized, "<script src=\"main.app.js\"></script>"));
        assertEquals(1, countOccurrences(normalized, "id=\"game-canvas\""));
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while (index >= 0) {
            index = text.indexOf(token, index);
            if (index >= 0) {
                count++;
                index += token.length();
            }
        }
        return count;
    }
}
