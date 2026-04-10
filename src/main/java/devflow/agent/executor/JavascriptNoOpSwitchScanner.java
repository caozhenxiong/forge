package devflow.agent.executor;

import devflow.agent.text.TextCanonicalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫描只包含 no-op 分支的 switch。
 *
 * <p>这类逻辑通常表示“输入通道已经接好，但行为仍未补齐”，适合作为
 * 完整性检查里的结构化提示。
 */
final class JavascriptNoOpSwitchScanner {

    private final ImplementationBodyNormalizer bodyNormalizer;
    private final JavascriptScannerSupport scannerSupport;

    JavascriptNoOpSwitchScanner(
            ImplementationBodyNormalizer bodyNormalizer,
            JavascriptScannerSupport scannerSupport
    ) {
        this.bodyNormalizer = bodyNormalizer;
        this.scannerSupport = scannerSupport;
    }

    List<ImplementationCompletenessFinding> scan(String source) {
        List<ImplementationCompletenessFinding> findings = new ArrayList<>();
        String value = source == null ? "" : source;
        int cursor = 0;
        while (cursor >= 0 && cursor < value.length()) {
            int switchIndex = value.indexOf("switch", cursor);
            if (switchIndex < 0) {
                break;
            }
            if (!scannerSupport.isKeywordBoundary(value, switchIndex - 1)
                    || !scannerSupport.isKeywordBoundary(value, switchIndex + "switch".length())) {
                cursor = switchIndex + "switch".length();
                continue;
            }
            int conditionStart = scannerSupport.skipWhitespace(value, switchIndex + "switch".length());
            if (conditionStart >= value.length() || value.charAt(conditionStart) != '(') {
                cursor = switchIndex + "switch".length();
                continue;
            }
            int conditionEnd = scannerSupport.findMatching(value, conditionStart, '(', ')');
            if (conditionEnd < 0) {
                cursor = switchIndex + "switch".length();
                continue;
            }
            int bodyStart = scannerSupport.skipWhitespace(value, conditionEnd + 1);
            if (bodyStart >= value.length() || value.charAt(bodyStart) != '{') {
                cursor = switchIndex + "switch".length();
                continue;
            }
            int bodyEnd = scannerSupport.findMatching(value, bodyStart, '{', '}');
            if (bodyEnd < 0) {
                cursor = switchIndex + "switch".length();
                continue;
            }
            if (containsOnlyNoOpCases(value.substring(bodyStart + 1, bodyEnd))) {
                findings.add(new ImplementationCompletenessFinding(
                        ImplementationCompletenessFindingType.EMPTY_BEHAVIOR,
                        null,
                        "switch handler contains only empty or no-op case branches"
                ));
            }
            cursor = bodyEnd + 1;
        }
        return findings;
    }

    private boolean containsOnlyNoOpCases(String switchBody) {
        List<String> caseBodies = extractSwitchCaseBodies(switchBody);
        return caseBodies.size() >= 2 && caseBodies.stream().allMatch(bodyNormalizer::isNoOpCaseBody);
    }

    private List<String> extractSwitchCaseBodies(String switchBody) {
        List<String> bodies = new ArrayList<>();
        List<String> lines = TextCanonicalizer.splitLines(switchBody);
        StringBuilder currentBody = null;
        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (isSwitchCaseLabel(trimmed)) {
                if (currentBody != null) {
                    bodies.add(currentBody.toString());
                }
                currentBody = new StringBuilder();
                continue;
            }
            if (currentBody == null) {
                continue;
            }
            if (!currentBody.isEmpty()) {
                currentBody.append('\n');
            }
            currentBody.append(rawLine);
        }
        if (currentBody != null) {
            bodies.add(currentBody.toString());
        }
        return bodies;
    }

    private boolean isSwitchCaseLabel(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.equals("default:") || (trimmed.startsWith("case ") && trimmed.endsWith(":"));
    }
}
