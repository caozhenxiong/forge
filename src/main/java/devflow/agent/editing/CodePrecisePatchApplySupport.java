package devflow.agent.editing;

import devflow.agent.parsing.ByteRange;
import devflow.agent.parsing.CodeStructureSnapshot;
import devflow.agent.parsing.CodeSymbol;
import devflow.agent.parsing.SourceLanguage;
import devflow.agent.text.TextCanonicalizer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 精确 patch 的 apply 支撑。
 *
 * <p>这里负责把结构化 patch 翻译成 UTF-8 字节替换，避免 `CodePreciseEditor`
 * 同时承担快照读取和字节级应用两类职责。
 */
final class CodePrecisePatchApplySupport {

    private final CodePreciseSymbolSupport symbolSupport;

    CodePrecisePatchApplySupport(CodePreciseSymbolSupport symbolSupport) {
        this.symbolSupport = symbolSupport;
    }

    String applyPatch(Path relativePath, String source, CodePrecisePatch patch) {
        CodeStructureSnapshot snapshot = symbolSupport.inspect(relativePath, source);
        boolean appendOnly = patch != null
                && patch.operations() != null
                && !patch.operations().isEmpty()
                && patch.operations().stream().allMatch(operation -> operation != null && operation.action() == CodePreciseAction.APPEND_FILE);
        boolean appendOnlySupported = snapshot.parseSummary().supported() && snapshot.parseSummary().valid();
        if (!snapshot.supportsPreciseEditing() && !(appendOnly && appendOnlySupported)) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.ANCHOR_MISSING,
                    "Current code file does not expose precise editing anchors."
            );
        }
        if (patch == null || !patch.hasAnyOperation()) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Precise code patch must contain at least one operation."
            );
        }

        int sourceLengthBytes = source.getBytes(StandardCharsets.UTF_8).length;
        List<Replacement> replacements = new ArrayList<>();
        for (CodePreciseOperation operation : patch.operations()) {
            if (operation == null || operation.action() == null) {
                continue;
            }
            if (operation.action() == CodePreciseAction.REPLACE_SYMBOL) {
                replacements.add(buildReplace(snapshot, operation));
            } else if (operation.action() == CodePreciseAction.REPLACE_SYMBOL_BODY) {
                replacements.add(buildReplaceBody(snapshot, operation));
            } else if (operation.action() == CodePreciseAction.INSERT_INTO_SYMBOL) {
                replacements.add(buildInsert(snapshot, operation));
            } else if (operation.action() == CodePreciseAction.APPEND_FILE) {
                replacements.add(new Replacement(
                        new ByteRange(sourceLengthBytes, sourceLengthBytes),
                        renderAppend(snapshot.language(), CodePatchContentResolver.resolve(operation))
                ));
            }
        }
        if (replacements.isEmpty()) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.PATCH_SCHEMA_INVALID,
                    "Precise code patch did not produce any applicable changes."
            );
        }
        return applyReplacements(source, replacements);
    }

    private Replacement buildReplace(CodeStructureSnapshot snapshot, CodePreciseOperation operation) {
        CodeSymbol symbol = symbolSupport.resolveSymbol(snapshot, operation.targetSymbol(), operation.targetKind(), false);
        return new Replacement(symbol.replaceRange(), stripTrailingWhitespace(CodePatchContentResolver.resolve(operation)) + "\n");
    }

    private Replacement buildReplaceBody(CodeStructureSnapshot snapshot, CodePreciseOperation operation) {
        CodeSymbol symbol = symbolSupport.resolveSymbol(snapshot, operation.targetSymbol(), operation.targetKind(), true);
        return new Replacement(symbol.bodyInnerRange(), stripTrailingWhitespace(CodePatchContentResolver.resolve(operation)));
    }

    private Replacement buildInsert(CodeStructureSnapshot snapshot, CodePreciseOperation operation) {
        CodeSymbol symbol = symbolSupport.resolveSymbol(snapshot, operation.targetSymbol(), operation.targetKind(), true);
        int insertionByte = symbol.bodyInnerRange().endByte();
        return new Replacement(new ByteRange(insertionByte, insertionByte), renderInsert(snapshot.language(), CodePatchContentResolver.resolve(operation)));
    }

    private String renderInsert(SourceLanguage language, String content) {
        String normalized = stripTrailingWhitespace(content);
        return "\n" + normalized + "\n";
    }

    private String renderAppend(SourceLanguage language, String content) {
        String normalized = stripTrailingWhitespace(content);
        return "\n\n" + normalized + "\n";
    }

    private String applyReplacements(String source, List<Replacement> replacements) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        List<Replacement> ordered = replacements.stream()
                .sorted(Comparator.comparingInt((Replacement replacement) -> replacement.range().startByte()).reversed())
                .toList();
        for (Replacement replacement : ordered) {
            bytes = replaceUtf8Range(bytes, replacement.range(), replacement.replacement());
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] replaceUtf8Range(byte[] bytes, ByteRange range, String replacement) {
        int start = Math.max(0, Math.min(range.startByte(), bytes.length));
        int end = Math.max(start, Math.min(range.endByte(), bytes.length));
        byte[] replacementBytes = replacement.getBytes(StandardCharsets.UTF_8);
        byte[] merged = new byte[start + replacementBytes.length + (bytes.length - end)];
        System.arraycopy(bytes, 0, merged, 0, start);
        System.arraycopy(replacementBytes, 0, merged, start, replacementBytes.length);
        System.arraycopy(bytes, end, merged, start + replacementBytes.length, bytes.length - end);
        return merged;
    }

    private String stripTrailingWhitespace(String content) {
        String value = content == null ? "" : content.strip();
        return TextCanonicalizer.trimTrailingHorizontalWhitespacePerLine(value);
    }

    private record Replacement(
            ByteRange range,
            String replacement
    ) {
    }
}
