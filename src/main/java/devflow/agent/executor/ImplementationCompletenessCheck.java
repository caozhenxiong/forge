package devflow.agent.executor;

import devflow.agent.context.ExecutionContract;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ImplementationCompletenessCheck {
    private final FileProjectWorkspace workspace;
    private final ImplementationPlaceholderScanner placeholderScanner;

    public ImplementationCompletenessCheck(FileProjectWorkspace workspace, TreeSitterSupport treeSitterSupport) {
        this.workspace = workspace;
        this.placeholderScanner = new ImplementationPlaceholderScanner(new ImplementationBehaviorScanner(treeSitterSupport));
    }

    public ImplementationCompletenessResult inspectProject(Path projectPath, ExecutionContract executionContract) {
        if (executionContract == null || (!executionContract.launchRequired() && !executionContract.surfaceRequired())) {
            return ImplementationCompletenessResult.success();
        }
        List<Path> relevantFiles = workspace.listProjectFiles(projectPath).stream()
                .filter(this::isRelevantImplementationFile)
                .toList();
        return inspectPaths(projectPath, relevantFiles, 2);
    }

    public ImplementationCompletenessResult inspectFiles(Path projectPath, List<Path> relativePaths) {
        if (relativePaths == null || relativePaths.isEmpty()) {
            return ImplementationCompletenessResult.success();
        }
        List<Path> relevantFiles = relativePaths.stream()
                .map(path -> path == null ? null : path.normalize())
                .filter(path -> path != null && isRelevantImplementationFile(path))
                .distinct()
                .toList();
        if (relevantFiles.isEmpty()) {
            return ImplementationCompletenessResult.success();
        }
        return inspectPaths(projectPath, relevantFiles, 1);
    }

    /**
     * 按“当前子任务责任域”检查完整性。
     * <p>
     * 这里不再简单地按整个文件通扫后直接阻塞，而是优先判断：
     * 1. 发现的问题是否落在当前子任务的 ownedCapabilities / acceptanceCriteria / goal 范围内；
     * 2. 若问题更像后续能力、预留扩展点或无直接责任归属，则仅作为非阻塞观察项，不否决当前子任务。
     */
    public ImplementationCompletenessResult inspectSubtask(Path projectPath, Subtask subtask) {
        if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
            return ImplementationCompletenessResult.success();
        }
        List<Path> relevantFiles = subtask.changes().stream()
                .map(FileChange::path)
                .filter(path -> path != null && !path.isBlank())
                .map(Path::of)
                .map(Path::normalize)
                .filter(this::isRelevantImplementationFile)
                .distinct()
                .toList();
        if (relevantFiles.isEmpty()) {
            return ImplementationCompletenessResult.success();
        }

        ImplementationCompletenessScopeProfile scopeProfile = ImplementationCompletenessScopeProfile.from(subtask);
        int placeholderMarkers = 0;
        int emptyBehaviors = 0;
        List<String> issues = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        List<String> deferredEvidence = new ArrayList<>();

        for (Path relativePath : relevantFiles) {
            String source = workspace.readFile(projectPath, relativePath);
            ImplementationPlaceholderInspection inspection = placeholderScanner.inspectSingleFile(relativePath, source);
            for (ImplementationCompletenessFinding finding : inspection.findings()) {
                if (isBlockingFinding(finding, scopeProfile)) {
                    if (finding.type() == ImplementationCompletenessFindingType.PLACEHOLDER_MARKER) {
                        placeholderMarkers++;
                    } else {
                        emptyBehaviors++;
                    }
                    addEvidence(evidence, relativePath.toString().replace('\\', '/') + ": " + finding.evidence());
                } else {
                    addEvidence(deferredEvidence, relativePath.toString().replace('\\', '/') + ": " + finding.evidence());
                }
            }
        }

        if (placeholderMarkers > 0) {
            issues.add("当前子任务负责范围内仍包含显式占位标记、TODO/FIXME 或未实现提示。");
        }
        if (emptyBehaviors > 0) {
            issues.add("当前子任务负责范围内仍包含空函数、空方法或 no-op 处理，行为实现尚未补齐。");
        }
        if (issues.isEmpty()) {
            return ImplementationCompletenessResult.success();
        }
        if (!deferredEvidence.isEmpty()) {
            addEvidence(evidence, "以下问题更接近后续能力或预留扩展点，本轮不作为阻塞项：");
            deferredEvidence.forEach(item -> addEvidence(evidence, item));
        }
        return ImplementationCompletenessResult.failure(placeholderMarkers, emptyBehaviors, issues, evidence);
    }

    private ImplementationCompletenessResult inspectPaths(Path projectPath, List<Path> relativePaths, int minimumBlockingEmptyBehaviors) {
        int placeholderMarkers = 0;
        int emptyBehaviors = 0;
        List<String> issues = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        for (Path relativePath : relativePaths) {
            if (relativePath == null) {
                continue;
            }
            String source = workspace.readFile(projectPath, relativePath);
            ImplementationPlaceholderInspection inspection = placeholderScanner.inspectSingleFile(relativePath, source);
            placeholderMarkers += inspection.placeholderMarkers();
            emptyBehaviors += inspection.emptyBehaviors();
            inspection.evidence().forEach(item -> addEvidence(evidence, item));
        }
        if (placeholderMarkers > 0) {
            issues.add("当前交付仍包含显式占位标记、TODO/FIXME 或未实现提示。");
        }
        if (emptyBehaviors >= minimumBlockingEmptyBehaviors) {
            issues.add("当前交付仍包含空函数、空方法或 no-op 处理，行为实现尚未补齐。");
        }
        if (issues.isEmpty()) {
            return ImplementationCompletenessResult.success();
        }
        return ImplementationCompletenessResult.failure(placeholderMarkers, emptyBehaviors, issues, evidence);
    }

    private boolean isRelevantImplementationFile(Path relativePath) {
        String path = relativePath.toString().toLowerCase();
        return ProjectPathSupport.isHtml(path) || ProjectPathSupport.isPreciseCode(path);
    }

    private void addEvidence(List<String> evidence, String item) {
        if (item == null || item.isBlank() || evidence.size() >= ImplementationCompletenessPolicy.maxEvidenceItems()) {
            return;
        }
        evidence.add(item.trim());
    }

    private boolean isBlockingFinding(
            ImplementationCompletenessFinding finding,
            ImplementationCompletenessScopeProfile scopeProfile
    ) {
        if (scopeProfile == null || scopeProfile.alwaysBlock()) {
            return true;
        }
        if (finding == null) {
            return false;
        }
        if (finding.symbolName() != null && !finding.symbolName().isBlank()) {
            return scopeProfile.matches(finding.symbolName());
        }
        return scopeProfile.matches(finding.evidence());
    }
}
