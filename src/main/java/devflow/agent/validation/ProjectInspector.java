package devflow.agent.validation;

import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
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
        Set<String> htmlEntries = new LinkedHashSet<>();

        for (Path relativePath : files) {
            String normalized = relativePath.toString().replace('\\', '/');
            fileNames.add(normalized);
            String fileName = relativePath.getFileName().toString();
            hasPom |= ProjectPathSupport.isMavenPom(fileName);
            hasGradleWrapper |= ProjectPathSupport.isGradleWrapper(fileName);
            hasGradleBuild |= ProjectPathSupport.isGradleBuildFile(fileName);
            hasPackageJson |= ProjectPathSupport.isNodeManifest(fileName);
            if (ProjectPathSupport.isHtml(relativePath)) {
                hasHtmlEntry = true;
                htmlEntries.add(normalized);
            }
            hasJavaScript |= ProjectPathSupport.isJavaScript(relativePath);
            hasTypeScript |= ProjectPathSupport.isTypeScript(relativePath);
        }

        PackageManagerType packageManager = ProjectPathSupport.detectPackageManager(fileNames, hasPackageJson);
        ProjectType projectType = ProjectPathSupport.detectProjectType(
                hasPom,
                hasGradleWrapper,
                hasGradleBuild,
                hasPackageJson,
                hasHtmlEntry
        );
        String htmlEntryPath = ProjectPathSupport.resolvePreferredHtmlEntry(htmlEntries);
        List<String> evidence = List.of(
                "projectType=" + projectType.key(),
                "packageManager=" + packageManager.key(),
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
                projectType.key(),
                packageManager.key(),
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

}
