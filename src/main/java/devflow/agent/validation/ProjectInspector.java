package devflow.agent.validation;

import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProjectInspector {

    private final FileProjectWorkspace workspace;

    public ProjectInspector(FileProjectWorkspace workspace) {
        this.workspace = workspace;
    }

    public ProjectFingerprint inspect(Path projectPath) {
        List<Path> files = workspace.listProjectFiles(projectPath);
        Set<String> fileNames = new LinkedHashSet<>();
        boolean hasPom = false;
        boolean hasGradleWrapper = false;
        boolean hasGradleBuild = false;
        boolean hasPackageJson = false;
        boolean hasHtmlEntry = false;
        boolean hasJavaScript = false;
        boolean hasTypeScript = false;
        Set<String> htmlEntries = new LinkedHashSet<>();

        for (Path relativePath : files) {
            String normalized = relativePath.toString().replace('\\', '/');
            fileNames.add(normalized);
            String fileName = relativePath.getFileName().toString();
            hasPom |= fileName.equals("pom.xml");
            hasGradleWrapper |= fileName.equals("gradlew");
            hasGradleBuild |= fileName.equals("build.gradle") || fileName.equals("build.gradle.kts");
            hasPackageJson |= fileName.equals("package.json");
            if (fileName.endsWith(".html")) {
                hasHtmlEntry = true;
                htmlEntries.add(normalized);
            }
            hasJavaScript |= fileName.endsWith(".js") || fileName.endsWith(".mjs") || fileName.endsWith(".cjs");
            hasTypeScript |= fileName.endsWith(".ts") || fileName.endsWith(".tsx");
        }

        String packageManager = "none";
        if (fileNames.contains("pnpm-lock.yaml")) {
            packageManager = "pnpm";
        } else if (fileNames.contains("yarn.lock")) {
            packageManager = "yarn";
        } else if (hasPackageJson) {
            packageManager = "npm";
        }

        String projectType = "unknown";
        if (hasPom) {
            projectType = "java-maven";
        } else if (hasGradleWrapper || hasGradleBuild) {
            projectType = "java-gradle";
        } else if (hasPackageJson && hasHtmlEntry) {
            projectType = "web-app";
        } else if (hasPackageJson) {
            projectType = "node-app";
        } else if (hasHtmlEntry) {
            projectType = "web-static";
        }

        String htmlEntryPath = resolveHtmlEntryPath(htmlEntries);
        List<String> evidence = List.of(
                "projectType=" + projectType,
                "packageManager=" + packageManager,
                "hasPom=" + hasPom,
                "hasGradleWrapper=" + hasGradleWrapper,
                "hasGradleBuild=" + hasGradleBuild,
                "hasPackageJson=" + hasPackageJson,
                "hasHtmlEntry=" + hasHtmlEntry,
                "htmlEntryPath=" + htmlEntryPath,
                "hasJavaScript=" + hasJavaScript,
                "hasTypeScript=" + hasTypeScript
        );

        return new ProjectFingerprint(
                projectType,
                packageManager,
                hasPom,
                hasGradleWrapper,
                hasGradleBuild,
                hasPackageJson,
                hasHtmlEntry,
                hasJavaScript,
                hasTypeScript,
                htmlEntryPath,
                fileNames,
                evidence
        );
    }

    private String resolveHtmlEntryPath(Set<String> htmlEntries) {
        if (htmlEntries.isEmpty()) {
            return "";
        }
        if (htmlEntries.contains("index.html")) {
            return "index.html";
        }
        for (String candidate : List.of("public/index.html", "dist/index.html", "src/index.html")) {
            if (htmlEntries.contains(candidate)) {
                return candidate;
            }
        }
        return htmlEntries.stream()
                .min(Comparator.<String>comparingInt(this::pathDepth).thenComparing(String::compareTo))
                .orElse("");
    }

    private int pathDepth(String path) {
        if (path == null || path.isBlank()) {
            return Integer.MAX_VALUE;
        }
        return (int) path.chars().filter(ch -> ch == '/').count();
    }
}
