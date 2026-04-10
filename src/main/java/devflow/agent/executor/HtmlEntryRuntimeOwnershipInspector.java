package devflow.agent.executor;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一检查 HTML 入口与派生 companion runtime 的所有权关系。
 *
 * <p>这层只维护一条规则：
 * 同一个 HTML 入口在任一时刻只能处于 INLINE_HOST 或 EXTERNAL_COMPANION 之一，
 * 不能既保留完整内联主脚本，又留下未接线或重复接线的 companion runtime。
 */
final class HtmlEntryRuntimeOwnershipInspector {

    HtmlEntryRuntimeOwnershipInspection inspectDeclared(
            Path projectPath,
            Path htmlEntryPath,
            String htmlSource,
            RuntimeOwnershipMode expectedMode,
            List<Path> availablePaths
    ) {
        if (expectedMode == null || htmlEntryPath == null || !ProjectPathSupport.isHtml(htmlEntryPath)) {
            return HtmlEntryRuntimeOwnershipInspection.success(expectedMode, htmlEntryPath, null);
        }
        Path companionRuntimePath = ProjectPathSupport.extractedInlineScriptAssetPath(htmlEntryPath).normalize();
        boolean companionPresent = companionExists(projectPath, companionRuntimePath, availablePaths);
        return inspect(htmlEntryPath.normalize(), htmlSource, expectedMode, companionRuntimePath, companionPresent);
    }

    HtmlEntryRuntimeOwnershipInspection inspectWorkspace(
            Path projectPath,
            Path htmlEntryPath,
            String htmlSource
    ) {
        if (htmlEntryPath == null || !ProjectPathSupport.isHtml(htmlEntryPath)) {
            return HtmlEntryRuntimeOwnershipInspection.success(null, htmlEntryPath, null);
        }
        Path normalizedHtmlPath = htmlEntryPath.normalize();
        Path companionRuntimePath = ProjectPathSupport.extractedInlineScriptAssetPath(normalizedHtmlPath).normalize();
        boolean companionPresent = projectPath != null && Files.exists(projectPath.resolve(companionRuntimePath));
        RuntimeOwnershipMode expectedMode = referencesCompanionRuntime(normalizedHtmlPath, htmlSource, companionRuntimePath)
                || companionPresent
                ? RuntimeOwnershipMode.EXTERNAL_COMPANION
                : RuntimeOwnershipMode.INLINE_HOST;
        return inspect(normalizedHtmlPath, htmlSource, expectedMode, companionRuntimePath, companionPresent);
    }

    private HtmlEntryRuntimeOwnershipInspection inspect(
            Path htmlEntryPath,
            String htmlSource,
            RuntimeOwnershipMode expectedMode,
            Path companionRuntimePath,
            boolean companionPresent
    ) {
        List<String> issues = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        boolean referencesCompanionRuntime = referencesCompanionRuntime(htmlEntryPath, htmlSource, companionRuntimePath);
        boolean keepsInlineAnchor = HtmlDocumentInspector.idSelectors(htmlSource).contains(TreeSitterSupport.APP_SCRIPT_ID);
        boolean keepsNonEmptyInlineScript = HtmlDocumentInspector.inlineScriptBodies(htmlSource).stream()
                .map(body -> body == null ? "" : body.trim())
                .anyMatch(body -> !body.isBlank());

        if (expectedMode == RuntimeOwnershipMode.EXTERNAL_COMPANION) {
            if (!referencesCompanionRuntime) {
                issues.add("宿主 HTML 已进入 EXTERNAL_COMPANION，但没有把 companion runtime script 接入运行时。");
            }
            if (!companionPresent) {
                issues.add("宿主 HTML 已进入 EXTERNAL_COMPANION，但 companion runtime 文件不存在。");
            }
            if (keepsInlineAnchor) {
                issues.add("宿主 HTML 已进入 EXTERNAL_COMPANION，但仍保留 app-script 内联锚点。");
            }
            if (keepsNonEmptyInlineScript) {
                issues.add("宿主 HTML 已进入 EXTERNAL_COMPANION，但仍保留非空内联脚本。");
            }
        } else if (expectedMode == RuntimeOwnershipMode.INLINE_HOST) {
            if (referencesCompanionRuntime) {
                issues.add("宿主 HTML 已声明 INLINE_HOST，不应同时接入 companion runtime script。");
            }
            if (companionPresent) {
                issues.add("宿主 HTML 已声明 INLINE_HOST，但入口目录中仍残留 companion runtime 文件。");
            }
        }

        if (!issues.isEmpty()) {
            evidence.add("htmlEntry=" + htmlEntryPath.toString().replace('\\', '/'));
            if (companionRuntimePath != null) {
                evidence.add("companionRuntime=" + companionRuntimePath.toString().replace('\\', '/'));
            }
            evidence.add("expectedMode=" + expectedMode);
        }
        return issues.isEmpty()
                ? HtmlEntryRuntimeOwnershipInspection.success(expectedMode, htmlEntryPath, companionRuntimePath)
                : HtmlEntryRuntimeOwnershipInspection.failure(expectedMode, htmlEntryPath, companionRuntimePath, issues, evidence);
    }

    private boolean companionExists(Path projectPath, Path companionRuntimePath, List<Path> availablePaths) {
        if (availablePaths != null) {
            for (Path availablePath : availablePaths) {
                if (availablePath != null && companionRuntimePath.equals(availablePath.normalize())) {
                    return true;
                }
            }
        }
        return projectPath != null && Files.exists(projectPath.resolve(companionRuntimePath));
    }

    private boolean referencesCompanionRuntime(Path htmlEntryPath, String htmlSource, Path companionRuntimePath) {
        if (htmlEntryPath == null || companionRuntimePath == null) {
            return false;
        }
        Path htmlParent = htmlEntryPath.getParent() == null ? Path.of("") : htmlEntryPath.getParent().normalize();
        for (String rawRef : HtmlDocumentInspector.referencedScriptPaths(htmlSource)) {
            if (rawRef == null || rawRef.isBlank() || ProjectPathSupport.isExternalReference(rawRef)) {
                continue;
            }
            Path resolved = htmlParent.resolve(rawRef).normalize();
            if (companionRuntimePath.equals(resolved)) {
                return true;
            }
        }
        return false;
    }
}
