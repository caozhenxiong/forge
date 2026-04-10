package devflow.agent.util;

import devflow.agent.validation.PackageManagerType;
import devflow.agent.validation.ProjectType;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 项目构建布局和入口派生规则。
 *
 * <p>这层维护：
 * 1. 项目类型、包管理器识别；
 * 2. HTML 入口优先级；
 * 3. 宿主内联脚本/样式的虚拟路径与外提资产路径。
 */
final class ProjectBuildLayoutRules {

    private static final List<String> HTML_ENTRY_CANDIDATES = List.of(
            "index.html",
            "public/index.html",
            "dist/index.html",
            "src/index.html"
    );

    PackageManagerType detectPackageManager(
            Set<String> fileNames,
            boolean hasNodeManifest,
            ProjectFileTypeRules fileTypeRules
    ) {
        if (fileNames != null) {
            for (String fileName : fileNames) {
                if (fileTypeRules.isPnpmLockFile(fileName)) {
                    return PackageManagerType.PNPM;
                }
                if (fileTypeRules.isYarnLockFile(fileName)) {
                    return PackageManagerType.YARN;
                }
            }
        }
        return hasNodeManifest ? PackageManagerType.NPM : PackageManagerType.NONE;
    }

    ProjectType detectProjectType(
            boolean hasPom,
            boolean hasGradleWrapper,
            boolean hasGradleBuild,
            boolean hasNodeManifest,
            boolean hasHtmlEntry
    ) {
        if (hasPom) {
            return ProjectType.JAVA_MAVEN;
        }
        if (hasGradleWrapper || hasGradleBuild) {
            return ProjectType.JAVA_GRADLE;
        }
        if (hasNodeManifest && hasHtmlEntry) {
            return ProjectType.WEB_APP;
        }
        if (hasNodeManifest) {
            return ProjectType.NODE_APP;
        }
        if (hasHtmlEntry) {
            return ProjectType.WEB_STATIC;
        }
        return ProjectType.UNKNOWN;
    }

    String resolvePreferredHtmlEntry(Set<String> htmlEntries) {
        if (htmlEntries == null || htmlEntries.isEmpty()) {
            return "";
        }
        for (String candidate : HTML_ENTRY_CANDIDATES) {
            if (htmlEntries.contains(candidate)) {
                return candidate;
            }
        }
        return htmlEntries.stream()
                .min(Comparator.<String>comparingInt(this::pathDepth).thenComparing(String::compareTo))
                .orElse("");
    }

    Path inlineScriptVirtualPath() {
        return Path.of("inline-script.js");
    }

    Path inlineStyleVirtualPath() {
        return Path.of("inline-style.css");
    }

    Path inlineScriptSyntheticPath(Path htmlPath) {
        if (htmlPath == null) {
            return inlineScriptVirtualPath();
        }
        return Path.of(htmlPath.toString() + ".inline.js");
    }

    Path inlineStyleSyntheticPath(Path htmlPath) {
        if (htmlPath == null) {
            return inlineStyleVirtualPath();
        }
        return Path.of(htmlPath.toString() + ".inline.css");
    }

    Path extractedInlineScriptAssetPath(Path htmlPath) {
        if (htmlPath == null) {
            return Path.of("inline-script.app.js");
        }
        Path fileName = htmlPath.getFileName();
        String baseName = fileName == null ? "inline-script" : stripExtension(fileName.toString());
        Path parent = htmlPath.getParent();
        Path assetName = Path.of(baseName + ".app.js");
        return parent == null ? assetName : parent.resolve(assetName);
    }

    private String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "inline-script";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private int pathDepth(String path) {
        if (path == null || path.isBlank()) {
            return Integer.MAX_VALUE;
        }
        return (int) path.chars().filter(ch -> ch == '/').count();
    }
}
