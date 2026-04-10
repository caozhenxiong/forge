package devflow.agent.util;

import java.util.List;
import java.util.Locale;

/**
 * 工作区忽略项和外部引用判定规则。
 *
 * <p>这层只维护稳定的工作区协议，不参与项目类型或文件语言判定。
 */
final class ProjectWorkspacePathRules {

    private static final List<String> WORKSPACE_IGNORED_DIRECTORY_NAMES = List.of(
            ".git",
            DevflowPathSupport.DEVFLOW_DIRECTORY,
            "target",
            "build",
            "out",
            "node_modules",
            ".idea"
    );
    private static final List<String> WORKSPACE_IGNORED_FILE_SUFFIXES = List.of(".class", ".jar");
    private static final List<String> EXTERNAL_REFERENCE_PREFIXES = List.of(
            "http://",
            "https://",
            "//",
            "data:",
            "mailto:",
            "#"
    );

    boolean isIgnoredWorkspaceDirectoryName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return WORKSPACE_IGNORED_DIRECTORY_NAMES.contains(value);
    }

    boolean isIgnoredWorkspaceArtifact(String fileName, ProjectFileTypeRules fileTypeRules) {
        return fileTypeRules.hasAnySuffix(fileName, WORKSPACE_IGNORED_FILE_SUFFIXES);
    }

    boolean isExternalReference(String rawRef) {
        String normalized = normalize(rawRef);
        if (normalized.isBlank()) {
            return false;
        }
        return EXTERNAL_REFERENCE_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    private String normalize(String path) {
        return path == null ? "" : path.trim().toLowerCase(Locale.ROOT);
    }
}
