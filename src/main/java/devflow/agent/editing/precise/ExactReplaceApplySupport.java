package devflow.agent.editing.precise;

import java.nio.file.Path;

/**
 * 宿主侧 exact-replace apply 内核。
 *
 * <p>这层只负责：
 * 1. 校验模型看到的内容指纹是否仍与当前内容一致；
 * 2. 校验 oldText 是否能在当前内容里精确命中；
 * 3. 执行确定性替换。
 *
 * <p>它不负责：
 * 1. 语法校验；
 * 2. 作用域判定；
 * 3. 流程重试或恢复。
 */
public final class ExactReplaceApplySupport {

    private final FileStateLedger fileStateLedger = new FileStateLedger();

    public String applyEdit(String targetPath, String source, ExactReplaceEdit edit) {
        String normalizedTargetPath = normalizeTargetPath(targetPath);
        String normalizedSource = source == null ? "" : source;
        FileStateSnapshot snapshot = fileStateLedger.capture(Path.of(normalizedTargetPath), normalizedSource);
        validateEdit(snapshot, normalizedTargetPath, edit);
        if (edit.oldText().isEmpty()) {
            if (!normalizedSource.isEmpty()) {
                throw new PreciseEditException(
                        PreciseEditFailureReason.TARGET_NOT_UNIQUE,
                        "Exact replace edit may use empty oldText only when the current content is empty."
                );
            }
            if (edit.newText().isEmpty()) {
                throw new PreciseEditException(
                        PreciseEditFailureReason.NO_MATERIAL_CHANGE,
                        "Exact replace edit must change the target content."
                );
            }
            return edit.newText();
        }
        int occurrences = countOccurrences(normalizedSource, edit.oldText());
        if (occurrences == 0) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.TARGET_NOT_FOUND,
                    "Exact replace edit oldText does not exist in the current content."
            );
        }
        if (!edit.replaceAll() && occurrences > 1) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.TARGET_NOT_UNIQUE,
                    "Exact replace edit oldText is not unique in the current content."
            );
        }
        String revised = edit.replaceAll()
                ? normalizedSource.replace(edit.oldText(), edit.newText())
                : replaceFirst(normalizedSource, edit.oldText(), edit.newText());
        if (revised.equals(normalizedSource)) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.NO_MATERIAL_CHANGE,
                    "Exact replace edit did not change the target content."
            );
        }
        return revised;
    }

    private void validateEdit(FileStateSnapshot snapshot, String expectedTargetPath, ExactReplaceEdit edit) {
        if (edit == null) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.MODEL_OUTPUT_INVALID,
                    "Exact replace edit payload is required."
            );
        }
        if (edit.targetPath().isBlank()) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.MODEL_OUTPUT_INVALID,
                    "Exact replace edit must declare targetPath."
            );
        }
        if (!normalizeTargetPath(edit.targetPath()).equals(expectedTargetPath)) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.MODEL_OUTPUT_INVALID,
                    "Exact replace edit targetPath does not match the current target."
            );
        }
        if (edit.baseContentHash().isBlank()) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.MODEL_OUTPUT_INVALID,
                    "Exact replace edit must declare baseContentHash."
            );
        }
        if (!edit.baseContentHash().equals(snapshot.contentHash())) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.SNAPSHOT_STALE,
                    "Exact replace edit was generated against stale content."
            );
        }
        if (edit.oldText().equals(edit.newText())) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.NO_MATERIAL_CHANGE,
                    "Exact replace edit oldText and newText must differ."
            );
        }
    }

    private String normalizeTargetPath(String value) {
        return value == null ? "" : value.strip().replace('\\', '/');
    }

    private int countOccurrences(String source, String target) {
        int occurrences = 0;
        int start = 0;
        while (true) {
            int index = source.indexOf(target, start);
            if (index < 0) {
                return occurrences;
            }
            occurrences++;
            start = index + Math.max(1, target.length());
        }
    }

    private String replaceFirst(String source, String target, String replacement) {
        int index = source.indexOf(target);
        if (index < 0) {
            return source;
        }
        return source.substring(0, index)
                + replacement
                + source.substring(index + target.length());
    }
}
