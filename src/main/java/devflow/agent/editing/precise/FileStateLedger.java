package devflow.agent.editing.precise;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 统一生成当前文件状态指纹。
 *
 * <p>这层只做确定性状态计算，不承载任何流程决策。
 */
public final class FileStateLedger {

    public FileStateSnapshot capture(Path relativePath, String content) {
        return capture(relativePath, true, content);
    }

    public FileStateSnapshot capture(Path relativePath, boolean exists, String content) {
        String normalized = content == null ? "" : content;
        return new FileStateSnapshot(
                relativePath,
                exists,
                normalized,
                sha256(normalized),
                lineCount(normalized)
        );
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private int lineCount(String content) {
        if (content.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) == '\n') {
                lines++;
            }
        }
        return lines;
    }
}
