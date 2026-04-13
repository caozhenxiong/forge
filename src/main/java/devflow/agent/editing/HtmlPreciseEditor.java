package devflow.agent.editing;

import devflow.agent.parsing.ByteRange;
import devflow.agent.parsing.HtmlEditableStructure;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.executor.editing.HtmlEditRegion;
import devflow.agent.text.TextCanonicalizer;
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

    public String extractRegionContent(String source, HtmlEditRegion region) {
        HtmlEditableStructure structure = treeSitterSupport.inspectEditableHtml(source);
        ByteRange range = rangeForRegion(structure, region);
        if (range == null || !range.isValid()) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.TARGET_NOT_ADDRESSABLE,
                    "Current HTML does not expose the requested focused region."
            );
        }
        return extractRange(source, range);
    }

    public String replaceRegionContent(String source, HtmlEditRegion region, String content) {
        if (region == HtmlEditRegion.SCRIPT) {
            return applyPatch(source, new HtmlPrecisePatch(null, null, content, null, null));
        }
        if (region == HtmlEditRegion.STYLE) {
            return applyPatch(source, new HtmlPrecisePatch(null, content, null, null, null));
        }
        return applyPatch(source, new HtmlPrecisePatch(content, null, null, null, null));
    }

    public String applyPatch(String source, HtmlPrecisePatch patch) {
        HtmlEditableStructure structure = treeSitterSupport.inspectEditableHtml(source);
        if (!structure.supportsPreciseEditing()) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.TARGET_NOT_ADDRESSABLE,
                    "Current HTML does not expose precise editing anchors."
            );
        }
        if (patch == null) {
            throw new PreciseEditException(
                    PreciseEditFailureReason.MODEL_OUTPUT_INVALID,
                    "Precise HTML patch must contain at least one section update."
            );
        }
        if (!patch.hasAnyChange()) {
            // 宿主 HTML 在外提脚本/样式后，后续子任务可能只需要确认“无需继续接线”。
            // 对这种显式 no-op patch 直接返回原文，避免把幂等结果误判成 schema 错误。
            return source;
        }

        List<Replacement> replacements = new ArrayList<>();
        boolean idempotentNoop = false;
        if (patch.markupHtml() != null) {
            replacements.add(new Replacement(structure.appRootInnerRange(), surroundWithNewlines(patch.markupHtml())));
        }
        if (patch.headAppendHtml() != null) {
            if (structure.headInnerRange() == null) {
                throw new PreciseEditException(
                        PreciseEditFailureReason.TARGET_NOT_ADDRESSABLE,
                        "Precise HTML patch requested headAppendHtml but the document has no editable <head> range."
                );
            }
            String fragment = stripTrailingWhitespace(patch.headAppendHtml());
            if (!fragment.isBlank() && !containsHtmlFragment(extractRange(source, structure.headInnerRange()), fragment)) {
                replacements.add(new Replacement(
                        new ByteRange(structure.headInnerRange().endByte(), structure.headInnerRange().endByte()),
                        "\n" + fragment + "\n"
                ));
            } else if (!fragment.isBlank()) {
                idempotentNoop = true;
            }
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
        if (patch.bodyAppendHtml() != null) {
            if (structure.bodyInnerRange() == null) {
                throw new PreciseEditException(
                        PreciseEditFailureReason.TARGET_NOT_ADDRESSABLE,
                        "Precise HTML patch requested bodyAppendHtml but the document has no editable <body> range."
                );
            }
            String fragment = stripTrailingWhitespace(patch.bodyAppendHtml());
            if (!fragment.isBlank() && !containsHtmlFragment(extractRange(source, structure.bodyInnerRange()), fragment)) {
                replacements.add(new Replacement(
                        new ByteRange(structure.bodyInnerRange().endByte(), structure.bodyInnerRange().endByte()),
                        "\n" + fragment + "\n"
                ));
            } else if (!fragment.isBlank()) {
                idempotentNoop = true;
            }
        }

        if (replacements.isEmpty()) {
            if (idempotentNoop) {
                return source;
            }
            throw new PreciseEditException(
                    PreciseEditFailureReason.MODEL_OUTPUT_INVALID,
                    "Precise HTML patch did not produce any applicable changes."
            );
        }

        return applyReplacements(source, replacements);
    }

    private String extractRange(String source, ByteRange range) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        int start = Math.max(0, Math.min(range.startByte(), bytes.length));
        int end = Math.max(start, Math.min(range.endByte(), bytes.length));
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }

    private ByteRange rangeForRegion(HtmlEditableStructure structure, HtmlEditRegion region) {
        if (structure == null || region == null) {
            return null;
        }
        if (region == HtmlEditRegion.SCRIPT) {
            return structure.appScriptInnerRange();
        }
        if (region == HtmlEditRegion.STYLE) {
            return structure.appStyleInnerRange();
        }
        return structure.appRootInnerRange();
    }

    private boolean containsHtmlFragment(String container, String fragment) {
        String normalizedContainer = normalizeHtml(container);
        String normalizedFragment = normalizeHtml(fragment);
        return !normalizedFragment.isBlank() && normalizedContainer.contains(normalizedFragment);
    }

    private String normalizeHtml(String value) {
        return TextCanonicalizer.normalizeHtmlFragment(value);
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
        return TextCanonicalizer.trimTrailingHorizontalWhitespacePerLine(value);
    }

    private record Replacement(
            ByteRange range,
            String replacement
    ) {
    }
}
