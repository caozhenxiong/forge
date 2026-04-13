package devflow.agent.executor.runtime;

import devflow.agent.executor.*;

import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * HTML 入口与其主运行时所有权的结构化契约。
 *
 * <p>这层只表达两件事：
 * 1. 当前入口由谁持有主运行时；
 * 2. 如果主运行时已外提，入口必须接入哪些 runtime 根脚本。
 *
 * <p>一旦这个契约已经确定，后续 planner / continuation / local gate /
 * architect check 都必须沿用它，而不是再从 prose 或默认文件名里反推。
 */
public record HtmlRuntimeOwnershipContract(
        Path htmlEntryPath,
        RuntimeOwnershipMode runtimeOwnership,
        List<Path> runtimePaths
) {

    public HtmlRuntimeOwnershipContract {
        htmlEntryPath = htmlEntryPath == null ? null : htmlEntryPath.normalize();
        runtimePaths = normalizeRuntimePaths(runtimePaths, htmlEntryPath);
        if (runtimeOwnership == RuntimeOwnershipMode.INLINE_HOST && !runtimePaths.isEmpty()) {
            runtimePaths = List.of();
        }
    }

    public static HtmlRuntimeOwnershipContract inlineHost(Path htmlEntryPath) {
        return new HtmlRuntimeOwnershipContract(htmlEntryPath, RuntimeOwnershipMode.INLINE_HOST, List.of());
    }

    public static HtmlRuntimeOwnershipContract externalCompanion(Path htmlEntryPath, List<Path> runtimePaths) {
        return new HtmlRuntimeOwnershipContract(htmlEntryPath, RuntimeOwnershipMode.EXTERNAL_COMPANION, runtimePaths);
    }

    public boolean active() {
        return htmlEntryPath != null
                && ProjectPathSupport.isHtml(htmlEntryPath)
                && runtimeOwnership != null;
    }

    public boolean externalCompanion() {
        return runtimeOwnership == RuntimeOwnershipMode.EXTERNAL_COMPANION;
    }

    public boolean inlineHost() {
        return runtimeOwnership == RuntimeOwnershipMode.INLINE_HOST;
    }

    public List<String> runtimePathStrings() {
        return runtimePaths.stream()
                .map(path -> path.toString().replace('\\', '/'))
                .toList();
    }

    private static List<Path> normalizeRuntimePaths(List<Path> runtimePaths, Path htmlEntryPath) {
        if (runtimePaths == null || runtimePaths.isEmpty()) {
            return List.of();
        }
        Path normalizedHtmlEntry = htmlEntryPath == null ? null : htmlEntryPath.normalize();
        Set<Path> normalized = new LinkedHashSet<>();
        for (Path runtimePath : runtimePaths) {
            if (runtimePath == null) {
                continue;
            }
            Path candidate = runtimePath.normalize();
            if (normalizedHtmlEntry != null && normalizedHtmlEntry.equals(candidate)) {
                continue;
            }
            if (!ProjectPathSupport.isRuntimeScript(candidate)) {
                continue;
            }
            normalized.add(candidate);
        }
        return List.copyOf(normalized);
    }
}
