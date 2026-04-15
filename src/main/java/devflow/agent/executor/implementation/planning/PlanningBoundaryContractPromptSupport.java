package devflow.agent.executor.implementation.planning;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * planning 阶段 shared-file capability boundary 的统一提示支撑。
 *
 * <p>这层只负责渲染 prompt 需要看到的确定性边界事实，
 * 避免 outline/detail 各自再解释一遍 shared-file 规则。
 */
final class PlanningBoundaryContractPromptSupport {

    private PlanningBoundaryContractPromptSupport() {
    }

    static String outlineSharedFileRuleBlock() {
        return """
                
                shared-file capability boundary 规则：
                1. 如果当前子任务与后续子任务共享同一路径，当前子任务必须把该后续子任务的 ownedCapabilities 全量写进 deferredCapabilities
                2. deferredCapabilities 不能留空占位，也不能只写后续 owner 能力的一部分
                3. 如果同一路径会被多个后续子任务继续修改，当前子任务必须分别锚定每个 future owner 的能力边界；若做不到，就应重新拆分 targetPaths
                4. outline 只负责声明 boundary contract，不要在这一层用 prose 模糊描述“后面再补”
                
                有效示例：
                - subtask-1 targetPaths=["index.html","src/app.js"] ownedCapabilities=["画布壳层"] deferredCapabilities=["gameplay"]
                - subtask-2 targetPaths=["src/app.js"] ownedCapabilities=["gameplay"] deferredCapabilities=[]
                
                无效示例：
                - subtask-1 与 subtask-2 共享 src/app.js，但 subtask-1 的 deferredCapabilities=[]
                - subtask-2 ownedCapabilities=["gameplay-core","gameplay-input"]，但 subtask-1 只 defer ["gameplay-core"]
                """;
    }

    static String sharedFileBoundaryContext(
            ImplementationOutline outline,
            ImplementationOutlineSubtask currentSubtask
    ) {
        if (outline == null || currentSubtask == null || outline.subtasks() == null || outline.subtasks().isEmpty()) {
            return "- 当前子任务没有可用的 shared-file boundary 上下文。";
        }
        int currentIndex = outline.subtasks().indexOf(currentSubtask);
        if (currentIndex < 0) {
            return "- 当前子任务没有可用的 shared-file boundary 上下文。";
        }
        List<String> currentPaths = normalizePaths(currentSubtask.targetPaths());
        if (currentPaths.isEmpty()) {
            return "- 当前子任务没有声明 targetPaths。";
        }
        List<String> lines = new ArrayList<>();
        for (int index = currentIndex + 1; index < outline.subtasks().size(); index++) {
            ImplementationOutlineSubtask futureSubtask = outline.subtasks().get(index);
            if (futureSubtask == null) {
                continue;
            }
            LinkedHashSet<String> overlap = new LinkedHashSet<>(currentPaths);
            overlap.retainAll(normalizePaths(futureSubtask.targetPaths()));
            if (overlap.isEmpty()) {
                continue;
            }
            List<String> futureOwned = safeCapabilities(futureSubtask.ownedCapabilities());
            if (futureOwned.isEmpty()) {
                continue;
            }
            lines.add("- 与 " + renderId(futureSubtask.id())
                    + " 共享文件 " + String.join("、", overlap)
                    + "；该后续子任务 ownedCapabilities=" + futureOwned
                    + "；当前 detail 不得提前实现这组 deferred capability。");
        }
        if (lines.isEmpty()) {
            return "- 当前子任务没有与后续子任务共享文件。";
        }
        return String.join("\n", lines);
    }

    private static List<String> normalizePaths(List<String> rawPaths) {
        if (rawPaths == null || rawPaths.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawPath : rawPaths) {
            if (rawPath == null || rawPath.isBlank()) {
                continue;
            }
            normalized.add(Path.of(rawPath).normalize().toString().replace('\\', '/'));
        }
        return List.copyOf(normalized);
    }

    private static List<String> safeCapabilities(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private static String renderId(String id) {
        return id == null || id.isBlank() ? "<unknown>" : id.trim();
    }
}
