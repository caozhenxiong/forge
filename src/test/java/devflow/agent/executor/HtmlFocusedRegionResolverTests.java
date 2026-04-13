package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

import devflow.agent.parsing.TreeSitterSupport;
import org.junit.jupiter.api.Test;

class HtmlFocusedRegionResolverTests {

    private final HtmlFocusedRegionResolver resolver = new HtmlFocusedRegionResolver(new TreeSitterSupport());

    @Test
    void prefersScriptRegionOverLargerMarkupAndStyleRegions() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <style id="app-style">
                    .shell { display: grid; }
                    .board { width: 320px; height: 640px; }
                    .panel { width: 160px; }
                  </style>
                </head>
                <body>
                  <main id="app-root">
                    <section class="shell">
                      <div class="board"></div>
                      <aside class="panel"></aside>
                    </section>
                  </main>
                  <script id="app-script">
                    const game = {};
                    function boot() {
                      return game;
                    }
                  </script>
                </body>
                </html>
                """;

        assertEquals(HtmlEditRegion.SCRIPT, resolver.resolvePrimaryRegion(html));
    }

    @Test
    void fallsBackToMarkupWhenScriptAnchorIsMissing() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <style id="app-style">body { background: black; }</style>
                </head>
                <body>
                  <main id="app-root"><div class="screen"></div></main>
                </body>
                </html>
                """;

        assertEquals(HtmlEditRegion.MARKUP, resolver.resolvePrimaryRegion(html));
    }
}
