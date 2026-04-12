package devflow.agent.executor;

import devflow.agent.editing.FileStateLedger;
import devflow.agent.editing.FileStateSnapshot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * diff-first prompt 的公共支撑。
 */
final class StructuredDiffPromptSupport {

    private static final FileStateLedger FILE_STATE_LEDGER = new FileStateLedger();

    private StructuredDiffPromptSupport() {
    }

    static FileStateSnapshot capture(Path relativePath, String currentContent) {
        return FILE_STATE_LEDGER.capture(relativePath, currentContent);
    }

    static String renderNumberedContent(String currentContent) {
        String normalized = currentContent == null ? "" : currentContent;
        if (normalized.isBlank()) {
            return "(empty file)";
        }
        String[] lines = normalized.split("\n", -1);
        List<String> numbered = new ArrayList<>(lines.length);
        for (int index = 0; index < lines.length; index++) {
            numbered.add("%4d | %s".formatted(index + 1, lines[index]));
        }
        return String.join("\n", numbered);
    }
}
