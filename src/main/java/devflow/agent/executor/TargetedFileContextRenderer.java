package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

/**
 * 负责把工程上下文压缩成“当前文件可见的最小相关视图”。
 *
 * <p>这层只做确定性渲染：
 * 1. 找出当前文件之外的相关变更文件；
 * 2. 补充运行时工作集推导出的关联文件；
 * 3. 以固定预览长度渲染成 targeted context。
 */
final class TargetedFileContextRenderer {

    private final FileProjectWorkspace workspace;
    private final RuntimeWorkingSetResolver runtimeWorkingSetResolver;

    TargetedFileContextRenderer(
            FileProjectWorkspace workspace,
            RuntimeWorkingSetResolver runtimeWorkingSetResolver
    ) {
        this.workspace = workspace;
        this.runtimeWorkingSetResolver = runtimeWorkingSetResolver;
    }

    String render(
            Path projectPath,
            List<FileChange> changes,
            Path currentPath,
            ContractView contractView,
            ProjectFingerprint fingerprint
    ) {
        List<FileChange> normalizedChanges = changes == null ? List.of() : changes;
        List<Path> changedPaths = normalizedChanges.stream()
                .map(FileChange::path)
                .filter(path -> path != null && !path.isBlank())
                .map(Path::of)
                .map(Path::normalize)
                .toList();
        List<Path> supplementalPaths = runtimeWorkingSetResolver.resolveSupplementalPaths(
                fingerprint,
                contractView == null ? null : contractView.executionContract(),
                changedPaths,
                currentPath
        );
        if (normalizedChanges.isEmpty() && supplementalPaths.isEmpty()) {
            return PlaceholderValues.machineNoRelatedFiles();
        }
        StringJoiner joiner = new StringJoiner("\n\n");
        for (FileChange change : normalizedChanges) {
            Path relativePath = Path.of(change.path()).normalize();
            if (currentPath != null && relativePath.equals(currentPath)) {
                continue;
            }
            joiner.add(renderFilePreview(projectPath, relativePath));
        }
        for (Path supplementalPath : supplementalPaths) {
            if (currentPath != null && supplementalPath.equals(currentPath)) {
                continue;
            }
            joiner.add(renderFilePreview(projectPath, supplementalPath));
        }
        String rendered = joiner.toString();
        return rendered.isBlank() ? PlaceholderValues.machineNoRelatedFiles() : rendered;
    }

    private String renderFilePreview(Path projectPath, Path relativePath) {
        String content = Files.exists(projectPath.resolve(relativePath))
                ? workspace.readFile(projectPath, relativePath)
                : PlaceholderValues.machineNewFile();
        return "## " + relativePath + "\n\n```text\n"
                + PlaceholderValues.truncateMiddle(content, GenerationBudgetProfile.fileContextPreviewChars())
                + "\n```";
    }
}
