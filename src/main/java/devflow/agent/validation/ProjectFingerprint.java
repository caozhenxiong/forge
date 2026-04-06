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
        Set<String> fileNames,
        List<String> evidence
) {
}
