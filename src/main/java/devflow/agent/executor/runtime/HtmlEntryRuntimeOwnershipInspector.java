package devflow.agent.executor.runtime;

import devflow.agent.executor.*;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一检查 HTML 入口与外提 runtime graph 的所有权关系。
 *
 * <p>这层只维护一条规则：
 * 同一个 HTML 入口在任一时刻只能处于 INLINE_HOST 或 EXTERNAL_COMPANION 之一，
 * 不能既保留完整内联主脚本，又留下未接线或重复接线的 external runtime roots。
 */
public final class HtmlEntryRuntimeOwnershipInspector {

    private final RuntimeScriptGraphInspector runtimeScriptGraphInspector;

    public HtmlEntryRuntimeOwnershipInspector() {
        this.runtimeScriptGraphInspector = new RuntimeScriptGraphInspector(new devflow.agent.project.FileProjectWorkspace());
    }

    public HtmlEntryRuntimeOwnershipInspection inspectDeclared(
            Path projectPath,
            String htmlSource,
            HtmlRuntimeOwnershipContract runtimeContract,
            List<Path> availablePaths
    ) {
        if (runtimeContract == null || !runtimeContract.active()) {
            return HtmlEntryRuntimeOwnershipInspection.success(null, List.of(), List.of(), false, false);
        }
        Set<Path> declaredRuntimePaths = new LinkedHashSet<>(runtimeContract.runtimePaths());
        Set<Path> availableRuntimePaths = normalizeAvailableRuntimePaths(availablePaths);
        Set<Path> referencedRuntimePaths = new LinkedHashSet<>(runtimeScriptGraphInspector.resolveDirectHtmlRuntimeScripts(
                runtimeContract.htmlEntryPath(),
                htmlSource,
                Set.copyOf(declaredRuntimePaths)
        ));
        referencedRuntimePaths.addAll(runtimeScriptGraphInspector.resolveInlineImportedRuntimeScripts(
                runtimeContract.htmlEntryPath(),
                htmlSource,
                Set.copyOf(declaredRuntimePaths)
        ));
        return inspect(
                projectPath,
                runtimeContract,
                htmlSource,
                Set.copyOf(referencedRuntimePaths),
                availableRuntimePaths
        );
    }

    public HtmlEntryRuntimeOwnershipInspection inspectWorkspace(
            Path projectPath,
            Path htmlEntryPath,
            String htmlSource,
            WebRuntimeAssetWiringInspection assetInspection
    ) {
        if (htmlEntryPath == null || !ProjectPathSupport.isHtml(htmlEntryPath)) {
            return HtmlEntryRuntimeOwnershipInspection.success(null, List.of(), List.of(), false, false);
        }
        Path normalizedHtmlPath = htmlEntryPath.normalize();
        WebRuntimeAssetWiringInspection resolvedAssetInspection = assetInspection == null
                ? new WebRuntimeAssetWiringInspection(List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
                : assetInspection;
        HtmlRuntimeOwnershipContract contract = resolvedAssetInspection.runtimeContract(normalizedHtmlPath);
        Set<Path> referencedRuntimePaths = new LinkedHashSet<>(resolvedAssetInspection.directHtmlRuntimeScripts());
        referencedRuntimePaths.addAll(resolvedAssetInspection.inlineImportedRuntimeScripts());
        return inspect(
                projectPath,
                contract,
                htmlSource,
                Set.copyOf(referencedRuntimePaths),
                Set.copyOf(resolvedAssetInspection.runtimeScripts())
        );
    }

    private HtmlEntryRuntimeOwnershipInspection inspect(
            Path projectPath,
            HtmlRuntimeOwnershipContract runtimeContract,
            String htmlSource,
            Set<Path> referencedRuntimePaths,
            Set<Path> availableRuntimePaths
    ) {
        if (runtimeContract == null || !runtimeContract.active()) {
            return HtmlEntryRuntimeOwnershipInspection.success(null, List.of(), List.of(), false, false);
        }
        Path htmlEntryPath = runtimeContract.htmlEntryPath();
        RuntimeOwnershipMode expectedMode = runtimeContract.runtimeOwnership();
        List<String> issues = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        boolean keepsInlineAnchor = HtmlDocumentInspector.idSelectors(htmlSource).contains(TreeSitterSupport.APP_SCRIPT_ID);
        boolean keepsNonEmptyInlineScript = HtmlDocumentInspector.inlineScriptBodies(htmlSource).stream()
                .map(body -> body == null ? "" : body.trim())
                .anyMatch(body -> !body.isBlank());
        Set<Path> reachableRuntimePaths = resolveReachableRuntimePaths(
                projectPath,
                referencedRuntimePaths,
                availableRuntimePaths
        );

        if (expectedMode == RuntimeOwnershipMode.EXTERNAL_COMPANION) {
            if (runtimeContract.runtimePaths().isEmpty()) {
                issues.add("宿主 HTML 已进入 EXTERNAL_COMPANION，但当前 contract 没有声明 runtime 根脚本。");
            }
            for (Path runtimePath : runtimeContract.runtimePaths()) {
                boolean present = isRuntimePathAvailable(projectPath, runtimePath, availableRuntimePaths);
                if (!present) {
                    issues.add("宿主 HTML 已进入 EXTERNAL_COMPANION，但 runtime 文件不存在: " + runtimePath.toString().replace('\\', '/'));
                    continue;
                }
                if (!reachableRuntimePaths.contains(runtimePath.normalize())) {
                    issues.add("宿主 HTML 已进入 EXTERNAL_COMPANION，但没有把 runtime script 接入运行时: "
                            + runtimePath.toString().replace('\\', '/'));
                }
            }
            if (keepsInlineAnchor) {
                issues.add("宿主 HTML 已进入 EXTERNAL_COMPANION，但仍保留 app-script 内联锚点。");
            }
            if (keepsNonEmptyInlineScript) {
                issues.add("宿主 HTML 已进入 EXTERNAL_COMPANION，但仍保留非空内联脚本。");
            }
        } else if (expectedMode == RuntimeOwnershipMode.INLINE_HOST) {
            if (!referencedRuntimePaths.isEmpty()) {
                issues.add("宿主 HTML 已声明 INLINE_HOST，不应同时接入 external runtime script。");
            }
        }

        if (!issues.isEmpty()) {
            evidence.add("htmlEntry=" + htmlEntryPath.toString().replace('\\', '/'));
            if (!runtimeContract.runtimePaths().isEmpty()) {
                evidence.add("runtimePaths=" + runtimeContract.runtimePathStrings());
            }
            evidence.add("expectedMode=" + expectedMode);
        }
        return issues.isEmpty()
                ? HtmlEntryRuntimeOwnershipInspection.success(
                runtimeContract,
                referencedRuntimePaths.stream().sorted().toList(),
                availableRuntimePaths.stream().sorted().toList(),
                keepsInlineAnchor,
                keepsNonEmptyInlineScript
        )
                : HtmlEntryRuntimeOwnershipInspection.failure(
                runtimeContract,
                referencedRuntimePaths.stream().sorted().toList(),
                availableRuntimePaths.stream().sorted().toList(),
                keepsInlineAnchor,
                keepsNonEmptyInlineScript,
                issues,
                evidence
        );
    }

    private Set<Path> resolveReachableRuntimePaths(
            Path projectPath,
            Set<Path> referencedRuntimePaths,
            Set<Path> availableRuntimePaths
    ) {
        if (referencedRuntimePaths == null || referencedRuntimePaths.isEmpty()) {
            return Set.of();
        }
        RuntimeScriptGraphInspector.RuntimeScriptGraph graph = new RuntimeScriptGraphInspector.RuntimeScriptGraph(
                availableRuntimePaths == null ? Set.of() : availableRuntimePaths,
                buildImports(projectPath, availableRuntimePaths)
        );
        return runtimeScriptGraphInspector.expandReachable(referencedRuntimePaths, graph);
    }

    private java.util.Map<Path, Set<Path>> buildImports(Path projectPath, Set<Path> availableRuntimePaths) {
        java.util.Map<Path, Set<Path>> imports = new java.util.LinkedHashMap<>();
        if (availableRuntimePaths == null || availableRuntimePaths.isEmpty()) {
            return java.util.Map.of();
        }
        for (Path runtimePath : availableRuntimePaths) {
            if (runtimePath == null) {
                continue;
            }
            Path parent = runtimePath.getParent() == null ? Path.of("") : runtimePath.getParent().normalize();
            java.util.Set<Path> resolvedImports = new java.util.LinkedHashSet<>();
            String source = "";
            if (projectPath != null && Files.exists(projectPath.resolve(runtimePath))) {
                try {
                    source = java.nio.file.Files.readString(projectPath.resolve(runtimePath));
                } catch (Exception ignored) {
                    source = "";
                }
            }
            for (String specifier : devflow.agent.parsing.JavaScriptLiteralScanner.extractImportSpecifiers(source)) {
                if (specifier == null || specifier.isBlank() || ProjectPathSupport.isExternalReference(specifier)) {
                    continue;
                }
                Path resolved = parent.resolve(specifier).normalize();
                if (availableRuntimePaths.contains(resolved)) {
                    resolvedImports.add(resolved);
                }
            }
            imports.put(runtimePath, java.util.Set.copyOf(resolvedImports));
        }
        return java.util.Map.copyOf(imports);
    }

    private boolean isRuntimePathAvailable(Path projectPath, Path runtimePath, Set<Path> availableRuntimePaths) {
        if (runtimePath == null) {
            return false;
        }
        if (availableRuntimePaths != null && availableRuntimePaths.contains(runtimePath.normalize())) {
            return true;
        }
        return projectPath != null && Files.exists(projectPath.resolve(runtimePath));
    }

    private Set<Path> normalizeAvailableRuntimePaths(List<Path> availablePaths) {
        if (availablePaths == null || availablePaths.isEmpty()) {
            return Set.of();
        }
        Set<Path> normalized = new LinkedHashSet<>();
        for (Path availablePath : availablePaths) {
            if (availablePath == null || !ProjectPathSupport.isRuntimeScript(availablePath)) {
                continue;
            }
            normalized.add(availablePath.normalize());
        }
        return Set.copyOf(normalized);
    }
}
