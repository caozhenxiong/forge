package devflow.agent.editing;

import devflow.agent.parsing.ByteRange;
import devflow.agent.parsing.CodeStructureSnapshot;
import devflow.agent.parsing.CodeSymbol;
import devflow.agent.parsing.SourceLanguage;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CodePreciseEditor {

    private final TreeSitterSupport treeSitterSupport;

    public CodePreciseEditor(TreeSitterSupport treeSitterSupport) {
        this.treeSitterSupport = treeSitterSupport;
    }

    public boolean supportsPreciseEditing(Path relativePath, String source) {
        return treeSitterSupport.inspectCodeStructure(relativePath, source).supportsPreciseEditing();
    }

    public String describeSymbols(Path relativePath, String source) {
        CodeStructureSnapshot snapshot = treeSitterSupport.inspectCodeStructure(relativePath, source);
        StringBuilder builder = new StringBuilder();
        builder.append("- language: ").append(snapshot.language()).append('\n');
        builder.append("- supportsPreciseEditing: ").append(snapshot.supportsPreciseEditing()).append('\n');
        int limit = Math.min(snapshot.symbols().size(), 12);
        for (int index = 0; index < limit; index++) {
            CodeSymbol symbol = snapshot.symbols().get(index);
            builder.append("- symbol: ")
                    .append(symbol.kind())
                    .append(" ")
                    .append(symbol.name())
                    .append(" insertion=")
                    .append(symbol.supportsInsertion())
                    .append('\n');
        }
        return builder.toString().strip();
    }

    public String applyPatch(Path relativePath, String source, CodePrecisePatch patch) {
        CodeStructureSnapshot snapshot = treeSitterSupport.inspectCodeStructure(relativePath, source);
        if (!snapshot.supportsPreciseEditing()) {
            throw new IllegalStateException("Current code file does not expose precise editing anchors.");
        }
        if (patch == null || !patch.hasAnyOperation()) {
            throw new IllegalStateException("Precise code patch must contain at least one operation.");
        }

        int sourceLengthBytes = source.getBytes(StandardCharsets.UTF_8).length;
        List<Replacement> replacements = new ArrayList<>();
        for (CodePreciseOperation operation : patch.operations()) {
            if (operation == null || operation.action() == null) {
                continue;
            }
            switch (operation.action()) {
                case REPLACE_SYMBOL -> replacements.add(buildReplace(snapshot, operation));
                case INSERT_INTO_SYMBOL -> replacements.add(buildInsert(snapshot, operation));
                case APPEND_FILE -> replacements.add(new Replacement(
                        new ByteRange(sourceLengthBytes, sourceLengthBytes),
                        renderAppend(snapshot.language(), operation.content())
                ));
            }
        }
        if (replacements.isEmpty()) {
            throw new IllegalStateException("Precise code patch did not produce any applicable changes.");
        }
        return applyReplacements(source, replacements);
    }

    private Replacement buildReplace(CodeStructureSnapshot snapshot, CodePreciseOperation operation) {
        CodeSymbol symbol = resolveSymbol(snapshot, operation.targetSymbol(), operation.targetKind(), false);
        return new Replacement(symbol.replaceRange(), stripTrailingWhitespace(operation.content()) + "\n");
    }

    private Replacement buildInsert(CodeStructureSnapshot snapshot, CodePreciseOperation operation) {
        CodeSymbol symbol = resolveSymbol(snapshot, operation.targetSymbol(), operation.targetKind(), true);
        int insertionByte = symbol.bodyInnerRange().endByte();
        return new Replacement(new ByteRange(insertionByte, insertionByte), renderInsert(snapshot.language(), operation.content()));
    }

    private CodeSymbol resolveSymbol(CodeStructureSnapshot snapshot, String name, String kind, boolean requireInsertion) {
        String normalizedName = normalize(name);
        String normalizedKind = normalize(kind);
        return snapshot.symbols().stream()
                .filter(symbol -> normalizedName.isBlank() || normalize(symbol.name()).equals(normalizedName))
                .filter(symbol -> normalizedKind.isBlank() || normalize(symbol.kind()).equals(normalizedKind))
                .filter(symbol -> !requireInsertion || symbol.supportsInsertion())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No matching symbol found for precise edit: name=%s kind=%s"
                        .formatted(name, kind)));
    }

    private String renderInsert(SourceLanguage language, String content) {
        String normalized = stripTrailingWhitespace(content);
        return switch (language) {
            case JAVA, PYTHON, GO -> "\n" + normalized + "\n";
            default -> "\n" + normalized + "\n";
        };
    }

    private String renderAppend(SourceLanguage language, String content) {
        String normalized = stripTrailingWhitespace(content);
        return switch (language) {
            case JAVA, PYTHON, GO -> "\n\n" + normalized + "\n";
            default -> "\n\n" + normalized + "\n";
        };
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
        return value.replaceAll("[ \\t]+(?=\\n)", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Replacement(
            ByteRange range,
            String replacement
    ) {
    }
}
