package devflow.agent.validation;

import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Path;
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

        for (Path relativePath : files) {
            String normalized = relativePath.toString().replace('\\', '/');
            fileNames.add(normalized);
            String fileName = relativePath.getFileName().toString();
            hasPom |= fileName.equals("pom.xml");
            hasGradleWrapper |= fileName.equals("gradlew");
            hasGradleBuild |= fileName.equals("build.gradle") || fileName.equals("build.gradle.kts");
            hasPackageJson |= fileName.equals("package.json");
            hasHtmlEntry |= fileName.endsWith(".html");
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

        List<String> evidence = List.of(
                "projectType=" + projectType,
                "packageManager=" + packageManager,
                "hasPom=" + hasPom,
                "hasGradleWrapper=" + hasGradleWrapper,
                "hasGradleBuild=" + hasGradleBuild,
                "hasPackageJson=" + hasPackageJson,
                "hasHtmlEntry=" + hasHtmlEntry,
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
                fileNames,
                evidence
        );
    }
}
