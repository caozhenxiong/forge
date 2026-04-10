package devflow.agent.util;

import java.util.List;
import java.util.Locale;

/**
 * 项目文件类型判定规则。
 *
 * <p>这里只维护稳定的文件后缀和文件名协议，避免路径类型判断继续散落在多个执行层里。
 */
final class ProjectFileTypeRules {

    private static final List<String> HTML_SUFFIXES = List.of(".html", ".htm");
    private static final List<String> STYLE_SUFFIXES = List.of(".css", ".scss");
    private static final List<String> JAVASCRIPT_SUFFIXES = List.of(".js", ".mjs", ".cjs");
    private static final List<String> TYPESCRIPT_SUFFIXES = List.of(".ts", ".tsx", ".mts", ".cts");
    private static final List<String> JAVA_SUFFIXES = List.of(".java");
    private static final List<String> KOTLIN_SUFFIXES = List.of(".kt");
    private static final List<String> JVM_SOURCE_SUFFIXES = List.of(".java", ".kt");
    private static final List<String> PYTHON_SUFFIXES = List.of(".py");
    private static final List<String> GO_SUFFIXES = List.of(".go");
    private static final List<String> SHELL_SUFFIXES = List.of(".sh");
    private static final List<String> NODE_MANIFEST_NAMES = List.of("package.json");
    private static final List<String> PNPM_LOCK_FILE_NAMES = List.of("pnpm-lock.yaml");
    private static final List<String> YARN_LOCK_FILE_NAMES = List.of("yarn.lock");
    private static final List<String> MAVEN_WRAPPER_FILE_NAMES = List.of("mvnw");
    private static final List<String> GRADLE_WRAPPER_FILE_NAMES = List.of("gradlew");
    private static final List<String> JVM_BUILD_FILE_NAMES = List.of("pom.xml", "build.gradle", "build.gradle.kts");
    private static final List<String> JAVASCRIPT_VALIDATION_ESM_SUFFIXES = List.of(".mjs");
    private static final List<String> JAVASCRIPT_VALIDATION_CJS_SUFFIXES = List.of(".cjs");
    private static final List<String> JAVASCRIPT_VALIDATION_DEFAULT_SUFFIXES = List.of(".js", ".mjs");

    boolean isHtml(String path) {
        return hasAnySuffix(path, HTML_SUFFIXES);
    }

    boolean isStyle(String path) {
        return hasAnySuffix(path, STYLE_SUFFIXES);
    }

    boolean isJavaScript(String path) {
        return hasAnySuffix(path, JAVASCRIPT_SUFFIXES);
    }

    boolean isTypeScript(String path) {
        return hasAnySuffix(path, TYPESCRIPT_SUFFIXES);
    }

    boolean isRuntimeScript(String path) {
        return isJavaScript(path) || isTypeScript(path);
    }

    boolean isJvmSource(String path) {
        return hasAnySuffix(path, JVM_SOURCE_SUFFIXES);
    }

    boolean isJava(String path) {
        return hasAnySuffix(path, JAVA_SUFFIXES);
    }

    boolean isKotlin(String path) {
        return hasAnySuffix(path, KOTLIN_SUFFIXES);
    }

    boolean isPython(String path) {
        return hasAnySuffix(path, PYTHON_SUFFIXES);
    }

    boolean isGo(String path) {
        return hasAnySuffix(path, GO_SUFFIXES);
    }

    boolean isShell(String path) {
        return hasAnySuffix(path, SHELL_SUFFIXES);
    }

    boolean isPreciseCode(String path) {
        return isRuntimeScript(path)
                || isJvmSource(path)
                || isPython(path)
                || isGo(path)
                || isStyle(path);
    }

    boolean isCommandCandidate(String path) {
        return isShell(path)
                || isPython(path)
                || isJavaScript(path)
                || isTypeScript(path)
                || isGo(path)
                || hasAnyFileName(path, NODE_MANIFEST_NAMES);
    }

    boolean isHttpEndpointCandidate(String path) {
        return isJavaScript(path)
                || isTypeScript(path)
                || isPython(path)
                || isGo(path)
                || isJvmSource(path)
                || hasAnyFileName(path, NODE_MANIFEST_NAMES)
                || hasAnyFileName(path, JVM_BUILD_FILE_NAMES);
    }

    boolean isNodeManifest(String path) {
        return hasAnyFileName(path, NODE_MANIFEST_NAMES);
    }

    String primaryNodeManifestFileName() {
        return NODE_MANIFEST_NAMES.getFirst();
    }

    boolean isMavenPom(String path) {
        return hasAnyFileName(path, List.of("pom.xml"));
    }

    boolean isGradleBuildFile(String path) {
        return hasAnyFileName(path, List.of("build.gradle", "build.gradle.kts"));
    }

    boolean isPnpmLockFile(String path) {
        return hasAnyFileName(path, PNPM_LOCK_FILE_NAMES);
    }

    boolean isYarnLockFile(String path) {
        return hasAnyFileName(path, YARN_LOCK_FILE_NAMES);
    }

    boolean isMavenWrapper(String path) {
        return hasAnyFileName(path, MAVEN_WRAPPER_FILE_NAMES);
    }

    boolean isGradleWrapper(String path) {
        return hasAnyFileName(path, GRADLE_WRAPPER_FILE_NAMES);
    }

    List<String> javaScriptValidationSuffixes(String path) {
        if (hasAnySuffix(path, List.of(".mjs", ".mts"))) {
            return JAVASCRIPT_VALIDATION_ESM_SUFFIXES;
        }
        if (hasAnySuffix(path, List.of(".cjs", ".cts"))) {
            return JAVASCRIPT_VALIDATION_CJS_SUFFIXES;
        }
        return JAVASCRIPT_VALIDATION_DEFAULT_SUFFIXES;
    }

    boolean hasAnySuffix(String path, List<String> suffixes) {
        String normalized = normalize(path);
        if (normalized.isBlank()) {
            return false;
        }
        return suffixes.stream().anyMatch(normalized::endsWith);
    }

    boolean hasAnyFileName(String path, List<String> fileNames) {
        String normalized = normalize(path);
        if (normalized.isBlank()) {
            return false;
        }
        return fileNames.stream().anyMatch(normalized::endsWith);
    }

    private String normalize(String path) {
        return path == null ? "" : path.trim().toLowerCase(Locale.ROOT);
    }
}
