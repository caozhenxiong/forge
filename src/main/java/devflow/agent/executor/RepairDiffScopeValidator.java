package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import java.util.ArrayList;
import java.util.List;

/**
 * syntax repair 的 diff 级作用域校验。
 *
 * <p>它不再解释符号名，也不靠领域语义裁判；唯一规则是：
 * repair 只能继续修正“当前失败补丁已经触达的行域”，不能借 repair 顺手扩成新的远端改动。
 */
final class RepairDiffScopeValidator {

    private static final int LINE_PADDING = 1;

    ToolResult verify(String baselineContent, String candidateContent, String repairedContent) {
        List<LineRange> allowedRanges = changedRanges(baselineContent, candidateContent, true);
        if (allowedRanges.isEmpty()) {
            return ToolResult.success(ToolName.CONTENT_VERIFY);
        }
        List<LineRange> repairRanges = changedRanges(candidateContent, repairedContent, false);
        for (LineRange repairRange : repairRanges) {
            if (!fitsAllowedRanges(repairRange, allowedRanges)) {
                return ToolResult.failure(
                        ToolName.CONTENT_VERIFY,
                        ToolFailureCode.TARGET_SCOPE_VIOLATION,
                        "repair changed lines outside the current failing diff region: " + repairRange,
                        "请只修复当前失败补丁已经触达的代码区域，不要在 syntax repair 中扩散到新的远端代码块。"
                );
            }
        }
        return ToolResult.success(ToolName.CONTENT_VERIFY);
    }

    private List<LineRange> changedRanges(String source, String target, boolean useTargetChunk) {
        List<String> sourceLines = splitLines(source);
        List<String> targetLines = splitLines(target);
        Patch<String> patch = DiffUtils.diff(sourceLines, targetLines);
        List<LineRange> ranges = new ArrayList<>();
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            int position = useTargetChunk ? delta.getTarget().getPosition() : delta.getSource().getPosition();
            int span = useTargetChunk ? delta.getTarget().size() : delta.getSource().size();
            int start = Math.max(0, position - LINE_PADDING);
            // 允许 repair 在当前差异块尾部补 1 行闭合 token，避免缺右括号/缺结束标签这类收尾修复被误伤。
            int endExclusive = Math.max(start + 1, position + Math.max(span, 1) + LINE_PADDING + 1);
            ranges.add(new LineRange(start, endExclusive));
        }
        return merge(ranges);
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        String normalized = content.endsWith("\n") ? content.substring(0, content.length() - 1) : content;
        if (normalized.isEmpty()) {
            return List.of("");
        }
        return List.of(normalized.split("\n", -1));
    }

    private boolean fitsAllowedRanges(LineRange candidate, List<LineRange> allowedRanges) {
        for (LineRange allowedRange : allowedRanges) {
            if (candidate.startInclusive() >= allowedRange.startInclusive()
                    && candidate.endExclusive() <= allowedRange.endExclusive() + 1) {
                return true;
            }
        }
        return false;
    }

    private List<LineRange> merge(List<LineRange> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }
        List<LineRange> ordered = ranges.stream()
                .sorted(java.util.Comparator.comparingInt(LineRange::startInclusive))
                .toList();
        List<LineRange> merged = new ArrayList<>();
        LineRange current = ordered.getFirst();
        for (int index = 1; index < ordered.size(); index++) {
            LineRange next = ordered.get(index);
            if (next.startInclusive() <= current.endExclusive()) {
                current = new LineRange(current.startInclusive(), Math.max(current.endExclusive(), next.endExclusive()));
                continue;
            }
            merged.add(current);
            current = next;
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    private record LineRange(int startInclusive, int endExclusive) {
    }
}
