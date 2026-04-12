package devflow.agent.editing;

import java.util.List;

/**
 * diff-first 主链的唯一补丁载荷。
 *
 * <p>expectedSourceHash 绑定当前文件状态；
 * hunks 只表达“当前状态 -> 目标状态”的最小局部差异。
 */
public record StructuredDiffPatch(
        String expectedSourceHash,
        List<StructuredDiffHunk> hunks
) {
    public StructuredDiffPatch {
        expectedSourceHash = expectedSourceHash == null ? "" : expectedSourceHash.trim().toLowerCase();
        hunks = hunks == null ? List.of() : hunks.stream()
                .filter(hunk -> hunk != null)
                .toList();
    }

    public boolean hasAnyHunk() {
        return !hunks.isEmpty();
    }
}
