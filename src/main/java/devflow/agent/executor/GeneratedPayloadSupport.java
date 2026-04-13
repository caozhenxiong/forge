package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.StructuredPayloadReader;

import devflow.agent.text.TextCanonicalizer;
import java.nio.file.Path;
import java.util.List;

/**
 * 统一处理生成结果的归一化与结构化 payload 读取。
 *
 * <p>模型返回内容经常会夹带文件头、fenced code block 或多余空白；
 * 这层把这些纯协议清洗逻辑从协调器中抽离。
 */
final class GeneratedPayloadSupport {

    private final StructuredPayloadReader structuredPayloadReader;

    GeneratedPayloadSupport(StructuredPayloadReader structuredPayloadReader) {
        this.structuredPayloadReader = structuredPayloadReader;
    }

    String normalizeGeneratedPayload(Path relativePath, String content) {
        String normalized = stripLeadingFileHeader(relativePath, nullToEmpty(content).strip());
        String fencedBlock = extractFencedBlock(normalized);
        if (fencedBlock != null) {
            return fencedBlock.strip();
        }
        return normalized.strip();
    }

    <T> T readStructuredPayload(String generated, Class<T> type) {
        return structuredPayloadReader.readJsonObject(generated, type);
    }

    private String stripLeadingFileHeader(Path relativePath, String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalizedPath = relativePath.toString().replace('\\', '/');
        String fileName = relativePath.getFileName() == null ? normalizedPath : relativePath.getFileName().toString();
        List<String> lines = TextCanonicalizer.splitLines(content);
        if (lines.size() < 2) {
            return content;
        }
        String firstLine = lines.getFirst().trim().replace('\\', '/');
        if (firstLine.equals(normalizedPath) || firstLine.equals(fileName)) {
            int newline = content.indexOf('\n');
            if (newline < 0 || newline + 1 >= content.length()) {
                return "";
            }
            return content.substring(newline + 1).stripLeading();
        }
        return content;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String extractFencedBlock(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.strip();
        if (!trimmed.startsWith("```")) {
            return null;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return null;
        }
        int closingFence = trimmed.lastIndexOf("```");
        if (closingFence <= firstNewline) {
            return null;
        }
        return trimmed.substring(firstNewline + 1, closingFence);
    }
}
