package devflow.agent.editing;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.ChangeDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.DeleteDelta;
import com.github.difflib.patch.InsertDelta;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 基于结构化 diff hunk 的本地 apply 支撑。
 *
 * <p>主链不再解释符号名或作用域边界，只校验两件事：
 * 1. 当前文件状态是否仍与模型看到的一致；
 * 2. hunk 是否能在指定行位点精确应用。
 */
public final class StructuredDiffPatchApplySupport {

    private final FileStateLedger fileStateLedger = new FileStateLedger();

    public String applyPatch(Path relativePath, String source, StructuredDiffPatch patch) {
        String normalizedSource = source == null ? "" : source;
        FileStateSnapshot snapshot = fileStateLedger.capture(relativePath, normalizedSource);
        validatePatch(snapshot, patch);
        LineDocument document = LineDocument.from(normalizedSource);
        Patch<String> diffPatch = new Patch<>();
        List<StructuredDiffHunk> orderedHunks = patch.hunks().stream()
                .sorted(Comparator.comparingInt(hunk -> hunk.sourceStartLine() == null ? Integer.MAX_VALUE : hunk.sourceStartLine()))
                .toList();
        for (StructuredDiffHunk hunk : orderedHunks) {
            diffPatch.addDelta(toDelta(snapshot.relativePath(), snapshot.lineCount(), hunk));
        }
        List<String> revisedLines;
        try {
            revisedLines = diffPatch.applyTo(new ArrayList<>(document.lines()));
        } catch (PatchFailedException exception) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.ANCHOR_MISSING,
                    "Structured diff patch could not be applied to current content: " + exception.getMessage()
            );
        }
        // 使用 diff-utils 重新计算一次 unified diff，确保最终变更是可重建的真实 diff，而不是只停留在中间状态。
        Patch<String> verifiedPatch = DiffUtils.diff(document.lines(), revisedLines);
        if (verifiedPatch.getDeltas().isEmpty()) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_EMPTY,
                    "Structured diff patch did not change the file."
            );
        }
        UnifiedDiffUtils.generateUnifiedDiff(
                pathLabel(relativePath),
                pathLabel(relativePath),
                document.lines(),
                verifiedPatch,
                3
        );
        return document.withLines(revisedLines);
    }

    private void validatePatch(FileStateSnapshot snapshot, StructuredDiffPatch patch) {
        if (patch == null || !patch.hasAnyHunk()) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Structured diff patch must contain at least one hunk."
            );
        }
        if (patch.expectedSourceHash().isBlank()) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Structured diff patch must declare expectedSourceHash."
            );
        }
        if (!patch.expectedSourceHash().equals(snapshot.contentHash())) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.ANCHOR_MISSING,
                    "Structured diff patch was generated against stale file state."
            );
        }
    }

    private com.github.difflib.patch.AbstractDelta<String> toDelta(
            Path relativePath,
            int lineCount,
            StructuredDiffHunk hunk
    ) {
        if (hunk == null || hunk.sourceStartLine() == null || hunk.sourceStartLine() <= 0) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Structured diff hunk for %s must use a positive 1-based sourceStartLine.".formatted(relativePath)
            );
        }
        int zeroBasedPosition = hunk.sourceStartLine() - 1;
        if (zeroBasedPosition > lineCount) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Structured diff hunk for %s points outside the current file.".formatted(relativePath)
            );
        }
        Chunk<String> sourceChunk = new Chunk<>(zeroBasedPosition, hunk.beforeLines());
        Chunk<String> targetChunk = new Chunk<>(zeroBasedPosition, hunk.afterLines());
        if (hunk.beforeLines().isEmpty() && hunk.afterLines().isEmpty()) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Structured diff hunk for %s cannot be empty.".formatted(relativePath)
            );
        }
        if (hunk.beforeLines().isEmpty()) {
            return new InsertDelta<>(sourceChunk, targetChunk);
        }
        if (hunk.afterLines().isEmpty()) {
            return new DeleteDelta<>(sourceChunk, targetChunk);
        }
        return new ChangeDelta<>(sourceChunk, targetChunk);
    }

    private String pathLabel(Path relativePath) {
        return relativePath == null ? "unknown-file" : relativePath.toString().replace('\\', '/');
    }

    private record LineDocument(
            List<String> lines,
            boolean endsWithNewline
    ) {
        static LineDocument from(String source) {
            if (source == null || source.isEmpty()) {
                return new LineDocument(List.of(), false);
            }
            boolean endsWithNewline = source.endsWith("\n");
            String normalized = endsWithNewline ? source.substring(0, source.length() - 1) : source;
            if (normalized.isEmpty()) {
                return new LineDocument(List.of(""), true);
            }
            return new LineDocument(List.of(normalized.split("\n", -1)), endsWithNewline);
        }

        String withLines(List<String> revisedLines) {
            if (revisedLines == null || revisedLines.isEmpty()) {
                return "";
            }
            String joined = String.join("\n", revisedLines);
            if (endsWithNewline || !joined.isBlank()) {
                return joined + "\n";
            }
            return joined;
        }
    }
}
