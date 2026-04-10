package devflow.agent.validation;

import java.util.List;
import java.util.Set;

public record ProjectFingerprint(
        String projectType,
        String packageManager,
        boolean hasPom,
        boolean hasGradleWrapper,
        boolean hasGradleBuild,
        boolean hasPackageJson,
        boolean hasHtmlEntry,
        boolean hasJavaScript,
        boolean hasTypeScript,
        String htmlEntryPath,
        Set<String> fileNames,
        List<String> evidence
) {

    public ProjectType projectTypeEnum() {
        return ProjectType.fromKey(projectType);
    }

    public PackageManagerType packageManagerType() {
        return PackageManagerType.fromKey(packageManager);
    }

    public String resolvedHtmlEntryPath() {
        return htmlEntryPath == null ? "" : htmlEntryPath;
    }

    public boolean hasResolvedHtmlEntry() {
        return !resolvedHtmlEntryPath().isBlank();
    }
}
