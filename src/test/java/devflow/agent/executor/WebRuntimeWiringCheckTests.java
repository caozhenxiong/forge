package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.toolloop.*;

import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebRuntimeWiringCheckTests {

    @TempDir
    Path tempDir;

    @Test
    void htmlEntryWithSiblingRuntimeModulesMustWireThemIntoRuntime() throws Exception {
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        Path htmlEntry = projectDir.resolve("index.html");
        Files.writeString(htmlEntry, """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>Tetris</title>
                </head>
                <body>
                  <main id="app-root">
                    <canvas id="game-board"></canvas>
                  </main>
                  <script>
                    console.log('boot');
                  </script>
                </body>
                </html>
                """);
        Files.writeString(projectDir.resolve("game-engine.js"), """
                export function startGame() {
                  return 'started';
                }
                """);

        TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
        HtmlStructureSnapshot snapshot = treeSitterSupport.inspectHtml(Files.readString(htmlEntry));
        WebRuntimeWiringCheck check = new WebRuntimeWiringCheck(new FileProjectWorkspace());

        WebRuntimeWiringResult result = check.inspect(
                projectDir,
                Path.of("index.html"),
                snapshot,
                Files.readString(htmlEntry)
        );

        assertFalse(result.passed());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("运行时")));
    }

    @Test
    void inlineScriptVariableNamesDoNotCountAsSiblingModuleWiring() throws Exception {
        Path projectDir = tempDir.resolve("project-inline");
        Files.createDirectories(projectDir);
        Path htmlEntry = projectDir.resolve("index.html");
        Files.writeString(htmlEntry, """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>Tetris</title>
                </head>
                <body>
                  <main id="app-root">
                    <canvas id="game-canvas"></canvas>
                  </main>
                  <script>
                    const game = { status: 'ready' };
                    console.log(game.status);
                  </script>
                </body>
                </html>
                """);
        Files.writeString(projectDir.resolve("game.js"), """
                export function createGame() {
                  return { status: 'wired' };
                }
                """);

        TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
        HtmlStructureSnapshot snapshot = treeSitterSupport.inspectHtml(Files.readString(htmlEntry));
        WebRuntimeWiringCheck check = new WebRuntimeWiringCheck(new FileProjectWorkspace());

        WebRuntimeWiringResult result = check.inspect(
                projectDir,
                Path.of("index.html"),
                snapshot,
                Files.readString(htmlEntry)
        );

        assertFalse(result.passed());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("运行时")));
    }

    @Test
    void htmlEntryWithNestedRuntimeModulesMustWireThemIntoRuntime() throws Exception {
        Path projectDir = tempDir.resolve("project-nested");
        Files.createDirectories(projectDir.resolve("js"));
        Path htmlEntry = projectDir.resolve("index.html");
        Files.writeString(htmlEntry, """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>Tetris</title>
                </head>
                <body>
                  <main id="app-root">
                    <canvas id="game-canvas"></canvas>
                  </main>
                  <script>
                    console.log('boot');
                  </script>
                </body>
                </html>
                """);
        Files.writeString(projectDir.resolve("js").resolve("game-engine.js"), """
                export function startGame() {
                  return 'started';
                }
                """);

        TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
        HtmlStructureSnapshot snapshot = treeSitterSupport.inspectHtml(Files.readString(htmlEntry));
        WebRuntimeWiringCheck check = new WebRuntimeWiringCheck(new FileProjectWorkspace());

        WebRuntimeWiringResult result = check.inspect(
                projectDir,
                Path.of("index.html"),
                snapshot,
                Files.readString(htmlEntry)
        );

        assertFalse(result.passed());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("运行时")));
        assertTrue(result.evidence().stream().anyMatch(item -> item.contains("js/game-engine.js")));
    }

    @Test
    void referencedNestedRuntimeScriptCountsAsWired() throws Exception {
        Path projectDir = tempDir.resolve("project-wired");
        Files.createDirectories(projectDir.resolve("js"));
        Path htmlEntry = projectDir.resolve("index.html");
        Files.writeString(htmlEntry, """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>Tetris</title>
                </head>
                <body>
                  <main id="app-root">
                    <canvas id="game-canvas"></canvas>
                  </main>
                  <script src="/js/game-engine.js"></script>
                </body>
                </html>
                """);
        Files.writeString(projectDir.resolve("js").resolve("game-engine.js"), """
                export function startGame() {
                  return 'started';
                }
                """);

        TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
        HtmlStructureSnapshot snapshot = treeSitterSupport.inspectHtml(Files.readString(htmlEntry));
        WebRuntimeWiringCheck check = new WebRuntimeWiringCheck(new FileProjectWorkspace());

        WebRuntimeWiringResult result = check.inspect(
                projectDir,
                Path.of("index.html"),
                snapshot,
                Files.readString(htmlEntry)
        );

        assertTrue(result.passed());
    }

    @Test
    void rootRelativeModuleImportsCountAsWired() throws Exception {
        Path projectDir = tempDir.resolve("project-root-import");
        Files.createDirectories(projectDir.resolve("js"));
        Path htmlEntry = projectDir.resolve("index.html");
        Files.writeString(htmlEntry, """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>Tetris</title>
                </head>
                <body>
                  <main id="app-root">
                    <canvas id="game-canvas"></canvas>
                  </main>
                  <script type="module" src="/js/game-engine.js"></script>
                </body>
                </html>
                """);
        Files.writeString(projectDir.resolve("js").resolve("game-engine.js"), """
                import { createCore } from "/js/core.js";
                export function startGame() {
                  return createCore();
                }
                """);
        Files.writeString(projectDir.resolve("js").resolve("core.js"), """
                export function createCore() {
                  return { ready: true };
                }
                """);

        TreeSitterSupport treeSitterSupport = new TreeSitterSupport();
        HtmlStructureSnapshot snapshot = treeSitterSupport.inspectHtml(Files.readString(htmlEntry));
        WebRuntimeWiringCheck check = new WebRuntimeWiringCheck(new FileProjectWorkspace());

        WebRuntimeWiringResult result = check.inspect(
                projectDir,
                Path.of("index.html"),
                snapshot,
                Files.readString(htmlEntry)
        );

        assertTrue(result.passed());
    }
}
