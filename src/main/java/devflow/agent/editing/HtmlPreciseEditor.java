package devflow.agent.editing;

import devflow.agent.parsing.ByteRange;
import devflow.agent.parsing.HtmlEditableStructure;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HtmlPreciseEditor {

    private final TreeSitterSupport treeSitterSupport;

    public HtmlPreciseEditor(TreeSitterSupport treeSitterSupport) {
        this.treeSitterSupport = treeSitterSupport;
    }

    public boolean supportsPreciseEditing(String source) {
        return treeSitterSupport.inspectEditableHtml(source).supportsPreciseEditing();
    }

    public String describeAnchors(String source) {
        HtmlEditableStructure structure = treeSitterSupport.inspectEditableHtml(source);
        return """
                - hasPreciseAnchors: %s
                - hasHeadAnchorRange: %s
                - hasBodyAnchorRange: %s
                - hasAppRoot: %s
                - hasAppStyle: %s
                - hasAppScript: %s
                """.formatted(
                structure.supportsPreciseEditing(),
                structure.headInnerRange() != null,
                structure.bodyInnerRange() != null,
                structure.appRootInnerRange() != null,
                structure.appStyleInnerRange() != null,
                structure.appScriptInnerRange() != null
        ).strip();
    }

    public String applyPatch(String source, HtmlPrecisePatch patch) {
        HtmlEditableStructure structure = treeSitterSupport.inspectEditableHtml(source);
        if (!structure.supportsPreciseEditing()) {
            throw new IllegalStateException("Current HTML does not expose precise editing anchors.");
        }
        if (patch == null || !patch.hasAnyChange()) {
            throw new IllegalStateException("Precise HTML patch must contain at least one section update.");
        }

        List<Replacement> replacements = new ArrayList<>();
        if (patch.markupHtml() != null) {
            replacements.add(new Replacement(structure.appRootInnerRange(), surroundWithNewlines(patch.markupHtml())));
        }
        if (patch.styleCss() != null) {
            if (structure.appStyleInnerRange() != null) {
                replacements.add(new Replacement(structure.appStyleInnerRange(), surroundWithNewlines(patch.styleCss())));
            } else if (structure.headInnerRange() != null) {
                replacements.add(new Replacement(
                        new ByteRange(structure.headInnerRange().endByte(), structure.headInnerRange().endByte()),
                        "\n<style id=\"" + TreeSitterSupport.APP_STYLE_ID + "\">\n"
                                + stripTrailingWhitespace(patch.styleCss())
                                + "\n</style>\n"
                ));
            }
        }
        if (patch.scriptJs() != null) {
            if (structure.appScriptInnerRange() != null) {
                replacements.add(new Replacement(structure.appScriptInnerRange(), surroundWithNewlines(patch.scriptJs())));
            } else if (structure.bodyInnerRange() != null) {
                replacements.add(new Replacement(
                        new ByteRange(structure.bodyInnerRange().endByte(), structure.bodyInnerRange().endByte()),
                        "\n<script id=\"" + TreeSitterSupport.APP_SCRIPT_ID + "\">\n"
                                + stripTrailingWhitespace(patch.scriptJs())
                                + "\n</script>\n"
                ));
            }
        }

        if (replacements.isEmpty()) {
            throw new IllegalStateException("Precise HTML patch did not produce any applicable changes.");
        }

        return applyReplacements(source, replacements);
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

    private String surroundWithNewlines(String content) {
        return "\n" + stripTrailingWhitespace(content) + "\n";
    }

    private String stripTrailingWhitespace(String content) {
        String value = content == null ? "" : content.strip();
        return value.replaceAll("[ \\t]+(?=\\n)", "");
    }

    private record Replacement(
            ByteRange range,
            String replacement
    ) {
    }
}
