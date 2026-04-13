package devflow.agent.quality;

import devflow.agent.executor.testing.RuntimeSnapshot;
import devflow.agent.executor.testing.RuntimeSnapshotCaptureStatus;
import devflow.agent.executor.testing.RuntimeSnapshotFailureCode;
import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一把静态 HTML 结构信号并入质量链使用的 runtime 信号。
 *
 * <p>implementation / review 在浏览器快照缺席时，仍然需要看到宿主入口里的
 * canvas / button 等结构事实；否则会把明显的高风险内联页面误判为普通静态页。
 */
final class HtmlStructureRuntimeSignalResolver {

    private final FileProjectWorkspace workspace = new FileProjectWorkspace();
    private final TreeSitterSupport treeSitterSupport = new TreeSitterSupport();

    RuntimeSnapshot resolve(Path projectPath, ProjectFingerprint fingerprint, RuntimeSnapshot runtimeSnapshot) {
        if (projectPath == null || fingerprint == null || !fingerprint.hasResolvedHtmlEntry()) {
            return runtimeSnapshot;
        }
        Path htmlEntryPath = Path.of(fingerprint.resolvedHtmlEntryPath()).normalize();
        if (!Files.exists(projectPath.resolve(htmlEntryPath))) {
            return runtimeSnapshot;
        }
        String htmlSource = workspace.readFile(projectPath, htmlEntryPath);
        HtmlStructureSnapshot snapshot = treeSitterSupport.inspectHtml(htmlSource);
        return mergeRuntimeSignals(fingerprint.resolvedHtmlEntryPath(), runtimeSnapshot, snapshot);
    }

    private RuntimeSnapshot mergeRuntimeSignals(
            String fallbackEntry,
            RuntimeSnapshot runtimeSnapshot,
            HtmlStructureSnapshot htmlSnapshot
    ) {
        if (htmlSnapshot == null) {
            return runtimeSnapshot;
        }
        int canvasCount = Math.max(runtimeSnapshot == null ? 0 : runtimeSnapshot.canvasCount(), htmlSnapshot.hasCanvas() ? 1 : 0);
        List<String> selectors = mergeSelectors(runtimeSnapshot == null ? List.of() : runtimeSnapshot.selectors(), htmlSnapshot.buttonSelectors());
        if (runtimeSnapshot == null) {
            return new RuntimeSnapshot(
                    fallbackEntry,
                    "",
                    null,
                    canvasCount,
                    selectors,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    RuntimeSnapshotCaptureStatus.DERIVED_STATIC,
                    RuntimeSnapshotFailureCode.NONE,
                    List.of()
            );
        }
        return new RuntimeSnapshot(
                runtimeSnapshot.entry() == null || runtimeSnapshot.entry().isBlank() ? fallbackEntry : runtimeSnapshot.entry(),
                runtimeSnapshot.pageTitle(),
                runtimeSnapshot.pageLoadMs(),
                canvasCount,
                selectors,
                safeList(runtimeSnapshot.surfaceCandidates()),
                safeList(runtimeSnapshot.controlCandidates()),
                safeList(runtimeSnapshot.exposedMetricKeys()),
                safeList(runtimeSnapshot.consoleErrors()),
                safeList(runtimeSnapshot.pageErrors()),
                runtimeSnapshot.captureStatus(),
                runtimeSnapshot.captureFailureCode(),
                safeList(runtimeSnapshot.captureErrors())
        );
    }

    private List<String> mergeSelectors(List<String> runtimeSelectors, Set<String> htmlButtonSelectors) {
        LinkedHashSet<String> selectors = new LinkedHashSet<>();
        selectors.addAll(safeList(runtimeSelectors));
        if (htmlButtonSelectors != null) {
            selectors.addAll(htmlButtonSelectors.stream()
                    .filter(selector -> selector != null && !selector.isBlank())
                    .map(String::trim)
                    .toList());
        }
        return List.copyOf(selectors);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
