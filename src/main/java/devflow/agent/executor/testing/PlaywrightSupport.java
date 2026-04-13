package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.PlaceholderValues;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Playwright 执行链的共享文件与文本工具。
 */
final class PlaywrightSupport {

    static final Path TESTCASE_SCRIPT_PATH =
            Path.of("tools", "playwright-smoke", "run-testcases.mjs").toAbsolutePath().normalize();

    String nonBlank(String stdout, String stderr) {
        return stdout != null && !stdout.isBlank() ? stdout : stderr;
    }

    String trim(String value) {
        return PlaceholderValues.truncateTail(value, 3000);
    }

    String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
