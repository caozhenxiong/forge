package devflow.agent.util;

import devflow.agent.validation.PackageManagerType;
import devflow.agent.validation.ProjectType;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 统一维护项目路径和文件类型的确定性判定规则。
 *
 * <p>这层只回答“这个路径属于哪一类稳定文件”，避免在 execution / gate /
 * context / validation 各层继续散落 `.endsWith(".js")` 这类难维护的魔法后缀。
 */
public final class ProjectPathSupport {
    private static final ProjectFileTypeRules FILE_TYPE_RULES = new ProjectFileTypeRules();
    private static final ProjectBuildLayoutRules BUILD_LAYOUT_RULES = new ProjectBuildLayoutRules();
    private static final ProjectWorkspacePathRules WORKSPACE_PATH_RULES = new ProjectWorkspacePathRules();

    private ProjectPathSupport() {
    }

    public static boolean isHtml(Path path) {
        return isHtml(path == null ? null : path.toString());
    }

    public static boolean isHtml(String path) {
        return FILE_TYPE_RULES.isHtml(path);
    }

    public static boolean isStyle(Path path) {
        return isStyle(path == null ? null : path.toString());
    }

    public static boolean isStyle(String path) {
        return FILE_TYPE_RULES.isStyle(path);
    }

    public static boolean isJavaScript(Path path) {
        return isJavaScript(path == null ? null : path.toString());
    }

    public static boolean isJavaScript(String path) {
        return FILE_TYPE_RULES.isJavaScript(path);
    }

    public static boolean isTypeScript(Path path) {
        return isTypeScript(path == null ? null : path.toString());
    }

    public static boolean isTypeScript(String path) {
        return FILE_TYPE_RULES.isTypeScript(path);
    }

    public static boolean isRuntimeScript(Path path) {
        return isRuntimeScript(path == null ? null : path.toString());
    }

    public static boolean isRuntimeScript(String path) {
        return FILE_TYPE_RULES.isRuntimeScript(path);
    }

    public static boolean isJvmSource(Path path) {
        return isJvmSource(path == null ? null : path.toString());
    }

    public static boolean isJvmSource(String path) {
        return FILE_TYPE_RULES.isJvmSource(path);
    }

    public static boolean isJava(Path path) {
        return isJava(path == null ? null : path.toString());
    }

    public static boolean isJava(String path) {
        return FILE_TYPE_RULES.isJava(path);
    }

    public static boolean isKotlin(Path path) {
        return isKotlin(path == null ? null : path.toString());
    }

    public static boolean isKotlin(String path) {
        return FILE_TYPE_RULES.isKotlin(path);
    }

    public static boolean isPython(Path path) {
        return isPython(path == null ? null : path.toString());
    }

    public static boolean isPython(String path) {
        return FILE_TYPE_RULES.isPython(path);
    }

    public static boolean isGo(Path path) {
        return isGo(path == null ? null : path.toString());
    }

    public static boolean isGo(String path) {
        return FILE_TYPE_RULES.isGo(path);
    }

    public static boolean isShell(Path path) {
        return isShell(path == null ? null : path.toString());
    }

    public static boolean isShell(String path) {
        return FILE_TYPE_RULES.isShell(path);
    }

    public static boolean isPreciseCode(Path path) {
        return isPreciseCode(path == null ? null : path.toString());
    }

    public static boolean isPreciseCode(String path) {
        return FILE_TYPE_RULES.isPreciseCode(path);
    }

    public static boolean isCommandCandidate(Path path) {
        return isCommandCandidate(path == null ? null : path.toString());
    }

    public static boolean isCommandCandidate(String path) {
        return FILE_TYPE_RULES.isCommandCandidate(path);
    }

    public static boolean isHttpEndpointCandidate(Path path) {
        return isHttpEndpointCandidate(path == null ? null : path.toString());
    }

    public static boolean isHttpEndpointCandidate(String path) {
        return FILE_TYPE_RULES.isHttpEndpointCandidate(path);
    }

    public static boolean hasAnySuffix(String path, List<String> suffixes) {
        return FILE_TYPE_RULES.hasAnySuffix(path, suffixes);
    }

    public static boolean hasAnyFileName(String path, List<String> fileNames) {
        return FILE_TYPE_RULES.hasAnyFileName(path, fileNames);
    }

    public static boolean isNodeManifest(String path) {
        return FILE_TYPE_RULES.isNodeManifest(path);
    }

    public static String primaryNodeManifestFileName() {
        return FILE_TYPE_RULES.primaryNodeManifestFileName();
    }

    public static boolean isMavenPom(String path) {
        return FILE_TYPE_RULES.isMavenPom(path);
    }

    public static boolean isGradleBuildFile(String path) {
        return FILE_TYPE_RULES.isGradleBuildFile(path);
    }

    public static boolean isPnpmLockFile(String path) {
        return FILE_TYPE_RULES.isPnpmLockFile(path);
    }

    public static boolean isYarnLockFile(String path) {
        return FILE_TYPE_RULES.isYarnLockFile(path);
    }

    public static boolean isMavenWrapper(String path) {
        return FILE_TYPE_RULES.isMavenWrapper(path);
    }

    public static boolean isGradleWrapper(String path) {
        return FILE_TYPE_RULES.isGradleWrapper(path);
    }

    public static PackageManagerType detectPackageManager(Set<String> fileNames, boolean hasNodeManifest) {
        return BUILD_LAYOUT_RULES.detectPackageManager(fileNames, hasNodeManifest, FILE_TYPE_RULES);
    }

    public static ProjectType detectProjectType(
            boolean hasPom,
            boolean hasGradleWrapper,
            boolean hasGradleBuild,
            boolean hasNodeManifest,
            boolean hasHtmlEntry
    ) {
        return BUILD_LAYOUT_RULES.detectProjectType(
                hasPom,
                hasGradleWrapper,
                hasGradleBuild,
                hasNodeManifest,
                hasHtmlEntry
        );
    }

    public static String resolvePreferredHtmlEntry(Set<String> htmlEntries) {
        return BUILD_LAYOUT_RULES.resolvePreferredHtmlEntry(htmlEntries);
    }

    public static Path inlineScriptVirtualPath() {
        return BUILD_LAYOUT_RULES.inlineScriptVirtualPath();
    }

    public static Path inlineStyleVirtualPath() {
        return BUILD_LAYOUT_RULES.inlineStyleVirtualPath();
    }

    /**
     * 把 HTML 入口里的主脚本映射成稳定的“虚拟源码路径”。
     *
     * <p>这不是物理文件路径，而是供 tree-sitter、precise editor 和事件日志共享的协议路径。
     * 集中到这里，避免各处继续手工拼接 `.inline.js`。
     */
    public static Path inlineScriptSyntheticPath(Path htmlPath) {
        return BUILD_LAYOUT_RULES.inlineScriptSyntheticPath(htmlPath);
    }

    /**
     * 把 HTML 入口里的主样式映射成稳定的“虚拟源码路径”。
     *
     * <p>这不是物理文件路径，而是供 tree-sitter、precise editor 和事件日志共享的协议路径。
     */
    public static Path inlineStyleSyntheticPath(Path htmlPath) {
        return BUILD_LAYOUT_RULES.inlineStyleSyntheticPath(htmlPath);
    }

    /**
     * 为宿主 HTML 计算默认的外提脚本资产路径。
     *
     * <p>这里采用与宿主同目录、同基名的稳定派生路径，
     * 避免继续在流程里散落 `game.js`、`app.js` 这类场景化命名。
     */
    public static Path extractedInlineScriptAssetPath(Path htmlPath) {
        return BUILD_LAYOUT_RULES.extractedInlineScriptAssetPath(htmlPath);
    }

    public static boolean isIgnoredWorkspaceDirectoryName(String value) {
        return WORKSPACE_PATH_RULES.isIgnoredWorkspaceDirectoryName(value);
    }

    public static boolean isIgnoredWorkspaceArtifact(String fileName) {
        return WORKSPACE_PATH_RULES.isIgnoredWorkspaceArtifact(fileName, FILE_TYPE_RULES);
    }

    public static boolean isRuntimeScriptAsset(Path path) {
        return isRuntimeScript(path);
    }

    public static boolean isStyleAsset(Path path) {
        return isStyle(path);
    }

    public static List<String> javaScriptValidationSuffixes(Path path) {
        return javaScriptValidationSuffixes(path == null ? null : path.toString());
    }

    /**
     * Node --check 需要根据模块制式选择合适的临时文件后缀。
     * 这里统一维护 suffix 选择，避免 gate / validation 再各写一套 if/endsWith。
     */
    public static List<String> javaScriptValidationSuffixes(String path) {
        return FILE_TYPE_RULES.javaScriptValidationSuffixes(path);
    }

    public static boolean isExternalReference(String rawRef) {
        return WORKSPACE_PATH_RULES.isExternalReference(rawRef);
    }
}
