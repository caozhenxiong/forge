package devflow.agent.executor;

import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.text.TextCanonicalizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 负责把 retry/review 反馈裁剪到当前文件与当前符号范围。
 *
 * <p>这层仍然只做确定性工作：
 * 1. 提取当前文件和兄弟文件的符号清单；
 * 2. 过滤明显只属于其他文件/其他符号的反馈；
 * 3. 输出统一的 `[FILE_SCOPED_RETRY]` 包装。
 */
final class FileFeedbackScopeSupport {

    private final FileProjectWorkspace workspace;
    private final PatchContextBuilder patchContextBuilder;

    FileFeedbackScopeSupport(
            FileProjectWorkspace workspace,
            PatchContextBuilder patchContextBuilder
    ) {
        this.workspace = workspace;
        this.patchContextBuilder = patchContextBuilder;
    }

    String scope(
            Path projectPath,
            Path relativePath,
            List<Path> changePaths,
            String existingContent,
            String feedback
    ) {
        if (feedback == null || feedback.isBlank()) {
            return "";
        }
        String fileName = relativePath.getFileName() == null ? relativePath.toString() : relativePath.getFileName().toString();
        List<String> currentSymbols = extractRelevantSymbols(relativePath, existingContent);
        List<String> siblingFileNames = changePaths.stream()
                .filter(path -> !path.equals(relativePath))
                .map(path -> path.getFileName() == null ? path.toString() : path.getFileName().toString())
                .distinct()
                .toList();
        List<String> siblingSymbols = changePaths.stream()
                .filter(path -> !path.equals(relativePath))
                .flatMap(path -> extractRelevantSymbols(
                        path,
                        Files.exists(projectPath.resolve(path)) ? workspace.readFile(projectPath, path) : ""
                ).stream())
                .distinct()
                .toList();

        List<String> keptLines = new ArrayList<>();
        for (String line : TextCanonicalizer.splitLines(feedback)) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("[")) {
                keptLines.add(line);
                continue;
            }
            boolean mentionsCurrent = mentionsToken(trimmed, fileName)
                    || mentionsToken(trimmed, relativePath.toString())
                    || containsAnyToken(trimmed, currentSymbols);
            boolean mentionsSibling = containsAnyToken(trimmed, siblingFileNames)
                    || containsAnyToken(trimmed, siblingSymbols);
            if (mentionsSibling && !mentionsCurrent) {
                continue;
            }
            keptLines.add(line);
        }
        if (keptLines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[FILE_SCOPED_RETRY]\n");
        builder.append("- currentFile: ").append(relativePath).append('\n');
        if (!currentSymbols.isEmpty()) {
            builder.append("- currentSymbols: ").append(String.join(", ", currentSymbols)).append('\n');
        }
        builder.append("- rule: 只处理与当前文件或当前符号清单相关的反馈；若反馈指向其他文件或其他符号，请忽略。\n");
        builder.append(String.join("\n", keptLines));
        return builder.toString().strip();
    }

    private List<String> extractRelevantSymbols(Path relativePath, String source) {
        try {
            return patchContextBuilder.relevantCodeSymbols(relativePath, source);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private boolean containsAnyToken(String line, List<String> tokens) {
        return tokens != null && tokens.stream().anyMatch(token -> mentionsToken(line, token));
    }

    private boolean mentionsToken(String line, String token) {
        if (line == null || line.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        String normalizedLine = line.toLowerCase(Locale.ROOT);
        String normalizedToken = token.toLowerCase(Locale.ROOT);
        int cursor = 0;
        while (cursor >= 0 && cursor < normalizedLine.length()) {
            int matchIndex = normalizedLine.indexOf(normalizedToken, cursor);
            if (matchIndex < 0) {
                return false;
            }
            if (isTokenBoundary(normalizedLine, matchIndex - 1)
                    && isTokenBoundary(normalizedLine, matchIndex + normalizedToken.length())) {
                return true;
            }
            cursor = matchIndex + normalizedToken.length();
        }
        return false;
    }

    private boolean isTokenBoundary(String source, int index) {
        if (index < 0 || index >= source.length()) {
            return true;
        }
        char current = source.charAt(index);
        return !Character.isLetterOrDigit(current) && current != '_' && current != '$';
    }
}
