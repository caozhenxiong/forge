package devflow.agent.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPathSupportTests {

    @Test
    void detectsStableProjectFileKinds() {
        assertTrue(ProjectPathSupport.isHtml("index.html"));
        assertTrue(ProjectPathSupport.isStyle("style.scss"));
        assertTrue(ProjectPathSupport.isJavaScript("game.mjs"));
        assertTrue(ProjectPathSupport.isTypeScript("game.tsx"));
        assertTrue(ProjectPathSupport.isJava("App.java"));
        assertTrue(ProjectPathSupport.isPython("main.py"));
        assertTrue(ProjectPathSupport.isGo("main.go"));
        assertTrue(ProjectPathSupport.isShell("run.sh"));
        assertTrue(ProjectPathSupport.isCommandCandidate("package.json"));
        assertTrue(ProjectPathSupport.isHttpEndpointCandidate("build.gradle.kts"));
        assertTrue(ProjectPathSupport.isPreciseCode("worker.ts"));
        assertTrue(ProjectPathSupport.isPreciseCode("style.css"));
        assertFalse(ProjectPathSupport.isPreciseCode("README.md"));
    }

    @Test
    void derivesStableInlineVirtualPaths() {
        assertEquals(Path.of("index.html.inline.js"), ProjectPathSupport.inlineScriptSyntheticPath(Path.of("index.html")));
        assertEquals(Path.of("index.html.inline.css"), ProjectPathSupport.inlineStyleSyntheticPath(Path.of("index.html")));
    }
}
