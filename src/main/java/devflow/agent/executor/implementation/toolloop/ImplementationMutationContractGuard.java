package devflow.agent.executor.implementation.toolloop;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.JavaScriptLiteralScanner;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.runtime.RuntimeOwnershipMode;
import devflow.agent.executor.subtask.ExecutionFileContractSet;
/**
 * implementation tool loop 的单次文件变更契约校验。
 *
 * <p>这里统一拦两类确定性问题：
 * 1. 当前文件新引入的本地依赖路径，必须属于当前 accepted change-set 或项目里已存在的资产；
 * 2. HTML 入口如果已经在 subtask detail 里声明了 runtimeOwnership，就必须按声明产出。
 *
 * <p>这样 coder 不能再通过“临时发明一个新文件名”“越界失败后回填成单文件 HTML”来绕过当前子任务边界。
 */
public final class ImplementationMutationContractGuard {

    private final HtmlRuntimeContractResolver runtimeContractResolver =
            new HtmlRuntimeContractResolver(new FileProjectWorkspace());

    public void validate(
            Path projectPath,
            Path relativePath,
            String content,
            ExecutionFileContractSet executionFileContract
    ) {
        validateDeclaredLocalReferences(
                projectPath,
                relativePath,
                content,
                executionFileContract == null ? Set.of() : executionFileContract.ownedPaths()
        );
        validateDeclaredHtmlRuntimeOwnership(
                projectPath,
                relativePath,
                content,
                executionFileContract == null ? List.of() : executionFileContract.declaredChanges()
        );
    }

    private void validateDeclaredLocalReferences(
            Path projectPath,
            Path relativePath,
            String content,
            Set<Path> ownedPaths
    ) {
        List<LocalReference> unresolved = collectLocalReferences(relativePath, content).stream()
                .filter(reference -> !isAllowedReference(projectPath, reference.resolvedPath(), ownedPaths))
                .toList();
        if (unresolved.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder("""
                当前文件引入了未声明的本地依赖路径；新依赖只能指向：
                1. 当前 accepted change-set 里的 owned files
                2. 项目中已经存在的本地资产

                未声明依赖：
                """.trim());
        for (LocalReference reference : unresolved) {
            builder.append("\n- `")
                    .append(relativePath.toString().replace('\\', '/'))
                    .append("` -> `")
                    .append(reference.resolvedPath().toString().replace('\\', '/'))
                    .append("` (")
                    .append(reference.kind())
                    .append(", raw=`")
                    .append(reference.rawReference())
                    .append("`)");
        }
        builder.append("\n如果确实需要新的本地资产，必须先把该路径显式纳入当前子任务的 accepted change-set。");
        throw new IllegalArgumentException(builder.toString());
    }

    private void validateDeclaredHtmlRuntimeOwnership(
            Path projectPath,
            Path relativePath,
            String content,
            List<FileChange> scopedChanges
    ) {
        if (relativePath == null || !ProjectPathSupport.isHtml(relativePath)) {
            return;
        }
        HtmlRuntimeOwnershipContract runtimeContract = runtimeContractResolver.resolveCanonicalContract(
                projectPath,
                relativePath,
                null,
                null,
                scopedChanges,
                content,
                List.of()
        );
        if (runtimeContract == null || !runtimeContract.active() || runtimeContract.runtimeOwnership() == null) {
            return;
        }
        Set<Path> referencedRuntimePaths = collectDeclaredHtmlRuntimeReferences(relativePath, content);
        boolean keepsNonEmptyInlineScript = HtmlDocumentInspector.inlineScriptBodies(content).stream()
                .map(body -> body == null ? "" : body.trim())
                .anyMatch(body -> !body.isBlank());
        if (runtimeContract.inlineHost()) {
            if (!referencedRuntimePaths.isEmpty()) {
                throw new IllegalArgumentException("宿主 HTML 已声明 INLINE_HOST，不应再接入 local companion runtime script。");
            }
            return;
        }
        if (runtimeContract.runtimePaths().isEmpty()) {
            throw new IllegalArgumentException("宿主 HTML 已声明 EXTERNAL_COMPANION，但当前 canonical runtime contract 没有声明 runtime 根脚本。");
        }
        if (referencedRuntimePaths.isEmpty()) {
            throw new IllegalArgumentException("宿主 HTML 已声明 EXTERNAL_COMPANION，必须接入至少一个 local runtime script。");
        }
        if (keepsNonEmptyInlineScript) {
            throw new IllegalArgumentException("宿主 HTML 已声明 EXTERNAL_COMPANION，不能继续保留非空内联主脚本。");
        }
        Set<Path> undeclaredReferences = referencedRuntimePaths.stream()
                .filter(path -> !runtimeContract.runtimePaths().contains(path))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!undeclaredReferences.isEmpty()) {
            throw new IllegalArgumentException(
                    "宿主 HTML 已声明 EXTERNAL_COMPANION，但接入了不在 canonical runtime contract 中声明的 runtime script: "
                            + undeclaredReferences.stream()
                            .map(path -> path.toString().replace('\\', '/'))
                            .toList()
            );
        }
    }

    private List<LocalReference> collectLocalReferences(Path relativePath, String content) {
        if (relativePath == null || content == null || content.isBlank()) {
            return List.of();
        }
        List<LocalReference> references = new ArrayList<>();
        if (ProjectPathSupport.isHtml(relativePath)) {
            for (String rawReference : HtmlDocumentInspector.referencedScriptPaths(content)) {
                addReference(references, relativePath, rawReference, LocalReferenceKind.HTML_SCRIPT_SRC, false);
            }
            for (String rawReference : HtmlDocumentInspector.referencedStylesheetPaths(content)) {
                addReference(references, relativePath, rawReference, LocalReferenceKind.HTML_STYLESHEET_HREF, false);
            }
            for (String inlineScript : HtmlDocumentInspector.inlineScriptBodies(content)) {
                for (String rawReference : JavaScriptLiteralScanner.extractImportSpecifiers(inlineScript)) {
                    addReference(references, relativePath, rawReference, LocalReferenceKind.INLINE_SCRIPT_IMPORT, true);
                }
            }
        }
        if (ProjectPathSupport.isRuntimeScript(relativePath)) {
            for (String rawReference : JavaScriptLiteralScanner.extractImportSpecifiers(content)) {
                addReference(references, relativePath, rawReference, LocalReferenceKind.MODULE_IMPORT, true);
            }
        }
        return List.copyOf(references);
    }

    private void addReference(
            List<LocalReference> references,
            Path ownerPath,
            String rawReference,
            LocalReferenceKind kind,
            boolean moduleImport
    ) {
        Path resolvedPath = resolveLocalReference(ownerPath, rawReference, moduleImport);
        if (resolvedPath != null) {
            references.add(new LocalReference(rawReference.trim(), resolvedPath, kind));
        }
    }

    private Path resolveLocalReference(Path ownerPath, String rawReference, boolean moduleImport) {
        if (rawReference == null || rawReference.isBlank()) {
            return null;
        }
        String normalizedReference = stripQueryAndFragment(rawReference.trim());
        if (normalizedReference.isBlank() || ProjectPathSupport.isExternalReference(normalizedReference)) {
            return null;
        }
        if (moduleImport && !looksLikeLocalModuleReference(normalizedReference)) {
            return null;
        }
        if (normalizedReference.startsWith("/")) {
            String projectRelative = normalizedReference.substring(1).trim();
            return projectRelative.isBlank() ? null : Path.of(projectRelative).normalize();
        }
        Path baseDirectory = ownerPath == null || ownerPath.getParent() == null
                ? Path.of("")
                : ownerPath.getParent().normalize();
        return baseDirectory.resolve(normalizedReference).normalize();
    }

    private String stripQueryAndFragment(String rawReference) {
        if (rawReference == null || rawReference.isBlank()) {
            return "";
        }
        int queryIndex = rawReference.indexOf('?');
        int fragmentIndex = rawReference.indexOf('#');
        int cutIndex = -1;
        if (queryIndex >= 0 && fragmentIndex >= 0) {
            cutIndex = Math.min(queryIndex, fragmentIndex);
        } else if (queryIndex >= 0) {
            cutIndex = queryIndex;
        } else if (fragmentIndex >= 0) {
            cutIndex = fragmentIndex;
        }
        return cutIndex < 0 ? rawReference : rawReference.substring(0, cutIndex);
    }

    private boolean looksLikeLocalModuleReference(String rawReference) {
        return rawReference.startsWith("./") || rawReference.startsWith("../") || rawReference.startsWith("/");
    }

    private boolean isAllowedReference(Path projectPath, Path resolvedPath, Set<Path> ownedPaths) {
        if (resolvedPath == null || projectPath == null) {
            return false;
        }
        Path normalized = resolvedPath.normalize();
        if (ownedPaths != null && ownedPaths.contains(normalized)) {
            return true;
        }
        Path absolutePath = projectPath.resolve(normalized).normalize();
        return absolutePath.startsWith(projectPath.normalize()) && Files.exists(absolutePath);
    }

    private Set<Path> collectDeclaredHtmlRuntimeReferences(Path htmlEntryPath, String content) {
        if (htmlEntryPath == null || content == null || content.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<Path> references = new LinkedHashSet<>();
        for (String rawReference : HtmlDocumentInspector.referencedScriptPaths(content)) {
            Path resolved = resolveLocalReference(htmlEntryPath, rawReference, false);
            if (resolved != null && ProjectPathSupport.isRuntimeScript(resolved)) {
                references.add(resolved);
            }
        }
        for (String inlineScript : HtmlDocumentInspector.inlineScriptBodies(content)) {
            for (String rawReference : JavaScriptLiteralScanner.extractImportSpecifiers(inlineScript)) {
                Path resolved = resolveLocalReference(htmlEntryPath, rawReference, true);
                if (resolved != null && ProjectPathSupport.isRuntimeScript(resolved)) {
                    references.add(resolved);
                }
            }
        }
        return Set.copyOf(references);
    }

    private record LocalReference(
            String rawReference,
            Path resolvedPath,
            LocalReferenceKind kind
    ) {
    }

    private enum LocalReferenceKind {
        HTML_SCRIPT_SRC,
        HTML_STYLESHEET_HREF,
        INLINE_SCRIPT_IMPORT,
        MODULE_IMPORT
    }
}
