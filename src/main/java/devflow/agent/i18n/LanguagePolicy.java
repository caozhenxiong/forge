package devflow.agent.i18n;

import devflow.agent.markdown.MarkdownSectionScanner;
import devflow.agent.text.TextCanonicalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 统一解析人类可读内容应使用的语言。机器可消费的 metadata 仍保持英文键，
 * 只把正文、评审说明和提示词文案切到对应语言。
 */
@Component
public class LanguagePolicy {

    private final DocumentLanguageProperties properties;

    public LanguagePolicy() {
        this(new DocumentLanguageProperties(null));
    }

    @Autowired
    public LanguagePolicy(DocumentLanguageProperties properties) {
        this.properties = properties;
    }

    public DocumentLanguage resolve(String... inputs) {
        if (inputs != null) {
            for (String input : inputs) {
                if (input == null || input.isBlank()) {
                    continue;
                }
                String humanFacingSample = stripMachineMetadata(input);
                if (!DocumentLanguage.containsHumanLanguage(humanFacingSample)) {
                    continue;
                }
                DocumentLanguage detected = DocumentLanguage.detectHumanLanguage(humanFacingSample);
                if (detected != null) {
                    return detected;
                }
            }
        }
        return properties.defaultLanguage();
    }

    private String stripMachineMetadata(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean insideMachineMetadata = false;
        for (String line : TextCanonicalizer.splitLines(input)) {
            if (isMachineMetadataHeading(line)) {
                insideMachineMetadata = true;
                continue;
            }
            if (insideMachineMetadata && MarkdownSectionScanner.isSecondLevelHeading(line)) {
                insideMachineMetadata = false;
            }
            if (insideMachineMetadata && isMachineMetadataEntry(line)) {
                continue;
            }
            if (!insideMachineMetadata || !line.isBlank()) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private boolean isMachineMetadataHeading(String line) {
        MarkdownSectionScanner.Heading heading = MarkdownSectionScanner.parseSecondLevelHeading(line);
        if (heading == null) {
            return false;
        }
        String normalized = MarkdownSectionScanner.normalizeHeadingTitle(heading.title());
        return normalized.equals(MarkdownSectionScanner.normalizeHeadingTitle(ArtifactLabels.contractMetadata(DocumentLanguage.EN)))
                || normalized.equals(MarkdownSectionScanner.normalizeHeadingTitle(ArtifactLabels.sourceMetadata(DocumentLanguage.EN)));
    }

    private boolean isMachineMetadataEntry(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.length() < 3) {
            return false;
        }
        char marker = trimmed.charAt(0);
        if (marker != '-' && marker != '*') {
            return false;
        }
        int cursor = 1;
        while (cursor < trimmed.length() && Character.isWhitespace(trimmed.charAt(cursor))) {
            cursor++;
        }
        int colonIndex = trimmed.indexOf(':', cursor);
        if (colonIndex <= cursor) {
            return false;
        }
        for (int index = cursor; index < colonIndex; index++) {
            char current = trimmed.charAt(index);
            boolean allowed = (current >= 'A' && current <= 'Z')
                    || (current >= 'a' && current <= 'z')
                    || (current >= '0' && current <= '9')
                    || current == '_' || current == '.' || current == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
