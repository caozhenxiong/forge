package devflow.agent.executor;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描对象成员里的空函数桩。
 *
 * <p>这类占位通常不会被 tree-sitter 的基础扫描命中，因此需要补一个
 * 轻量的字符级扫描器。
 */
final class JavascriptPropertyStubScanner {

    private final ImplementationBodyNormalizer bodyNormalizer;
    private final JavascriptScannerSupport scannerSupport;

    JavascriptPropertyStubScanner(
            ImplementationBodyNormalizer bodyNormalizer,
            JavascriptScannerSupport scannerSupport
    ) {
        this.bodyNormalizer = bodyNormalizer;
        this.scannerSupport = scannerSupport;
    }

    List<ImplementationCompletenessFinding> scan(String source) {
        List<ImplementationCompletenessFinding> findings = new ArrayList<>();
        String value = source == null ? "" : source;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != ':') {
                continue;
            }
            String propertyName = scannerSupport.extractPropertyIdentifierBeforeColon(value, index);
            if (propertyName.isBlank()) {
                continue;
            }
            int cursor = scannerSupport.skipWhitespace(value, index + 1);
            if (cursor >= value.length()) {
                continue;
            }
            if (value.charAt(cursor) == '(') {
                scanArrowStub(findings, value, propertyName, cursor);
                continue;
            }
            scanFunctionStub(findings, value, propertyName, cursor);
        }
        return findings;
    }

    private void scanArrowStub(
            List<ImplementationCompletenessFinding> findings,
            String source,
            String propertyName,
            int cursor
    ) {
        int argsEnd = scannerSupport.findMatching(source, cursor, '(', ')');
        if (argsEnd < 0) {
            return;
        }
        int arrowStart = scannerSupport.skipWhitespace(source, argsEnd + 1);
        if (!scannerSupport.startsWithArrow(source, arrowStart)) {
            return;
        }
        int bodyStart = scannerSupport.skipWhitespace(source, arrowStart + 2);
        if (!isEmptyBracedBlock(source, bodyStart)) {
            return;
        }
        findings.add(new ImplementationCompletenessFinding(
                ImplementationCompletenessFindingType.EMPTY_BEHAVIOR,
                propertyName,
                "object member " + propertyName + " is an empty arrow-function stub"
        ));
    }

    private void scanFunctionStub(
            List<ImplementationCompletenessFinding> findings,
            String source,
            String propertyName,
            int cursor
    ) {
        if (!scannerSupport.startsWithKeyword(source, cursor, "function")) {
            return;
        }
        int functionCursor = scannerSupport.skipWhitespace(source, cursor + "function".length());
        if (functionCursor < source.length()
                && scannerSupport.isIdentifierStart(source.charAt(functionCursor))) {
            while (functionCursor < source.length()
                    && scannerSupport.isIdentifierPart(source.charAt(functionCursor))) {
                functionCursor++;
            }
            functionCursor = scannerSupport.skipWhitespace(source, functionCursor);
        }
        if (functionCursor >= source.length() || source.charAt(functionCursor) != '(') {
            return;
        }
        int argsEnd = scannerSupport.findMatching(source, functionCursor, '(', ')');
        if (argsEnd < 0) {
            return;
        }
        int bodyStart = scannerSupport.skipWhitespace(source, argsEnd + 1);
        if (!isEmptyBracedBlock(source, bodyStart)) {
            return;
        }
        findings.add(new ImplementationCompletenessFinding(
                ImplementationCompletenessFindingType.EMPTY_BEHAVIOR,
                propertyName,
                "object member " + propertyName + " is an empty function stub"
        ));
    }

    private boolean isEmptyBracedBlock(String source, int blockStart) {
        if (blockStart < 0 || blockStart >= source.length() || source.charAt(blockStart) != '{') {
            return false;
        }
        int blockEnd = scannerSupport.findMatching(source, blockStart, '{', '}');
        if (blockEnd < 0) {
            return false;
        }
        String body = source.substring(blockStart + 1, blockEnd);
        return bodyNormalizer.normalize(body).isBlank();
    }
}
