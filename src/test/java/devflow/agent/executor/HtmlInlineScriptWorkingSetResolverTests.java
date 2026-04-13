package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.TreeSitterSupport;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HtmlInlineScriptWorkingSetResolverTests {

    @Test
    void singleEntryBootstrapScriptIsNotEligibleForWorksetEditing() {
        HtmlInlineScriptWorkingSetResolver resolver = new HtmlInlineScriptWorkingSetResolver(
                new TreeSitterSupport(),
                new TreeSitterTargetLocator(new TreeSitterSupport())
        );

        InlineScriptWorkingSet workingSet = resolver.resolve(
                Path.of("index.html"),
                """
                <!DOCTYPE html>
                <html>
                <body>
                  <script id="app-script">
                    function bootstrap() {
                    }

                    document.addEventListener('DOMContentLoaded', bootstrap);
                  </script>
                </body>
                </html>
                """
        );

        assertNull(workingSet, "单入口 bootstrap 脚本不应再进入 inline-script-workset");
    }

    @Test
    void multiSymbolScriptRemainsEligibleForWorksetEditing() {
        HtmlInlineScriptWorkingSetResolver resolver = new HtmlInlineScriptWorkingSetResolver(
                new TreeSitterSupport(),
                new TreeSitterTargetLocator(new TreeSitterSupport())
        );

        InlineScriptWorkingSet workingSet = resolver.resolve(
                Path.of("index.html"),
                """
                <!DOCTYPE html>
                <html>
                <body>
                  <script id="app-script">
                    function bindButton() {
                      document.getElementById('start-btn')?.addEventListener('click', bootstrap);
                    }

                    function bootstrap() {
                      bindButton();
                    }

                    document.addEventListener('DOMContentLoaded', bootstrap);
                  </script>
                </body>
                </html>
                """
        );

        assertNotNull(workingSet);
        assertEquals(Path.of("index.html.inline.js"), workingSet.syntheticPath());
    }
}
