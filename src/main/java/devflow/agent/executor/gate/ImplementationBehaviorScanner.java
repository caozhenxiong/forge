package devflow.agent.executor.gate;

import devflow.agent.executor.*;

import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.CodeStructureSnapshot;
import devflow.agent.parsing.CodeSymbol;
import devflow.agent.parsing.SourceLanguage;
import devflow.agent.parsing.TreeSitterSupport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 负责代码级空实现/no-op 扫描。
 * 这层只关心源码行为特征，不负责文件筛选或责任域匹配。
 */
final class ImplementationBehaviorScanner {

    private final TreeSitterSupport treeSitterSupport;
    private final ImplementationBodyNormalizer bodyNormalizer;
    private final ImplementationJavascriptBehaviorScanner javascriptBehaviorScanner;

    ImplementationBehaviorScanner(TreeSitterSupport treeSitterSupport) {
        this.treeSitterSupport = treeSitterSupport;
        this.bodyNormalizer = new ImplementationBodyNormalizer();
        this.javascriptBehaviorScanner = new ImplementationJavascriptBehaviorScanner(bodyNormalizer);
    }

    ImplementationBehaviorInspection inspectCodeLikeContent(SourceLanguage language, String source) {
        List<ImplementationCompletenessFinding> findings = new ArrayList<>();

        CodeStructureSnapshot snapshot = treeSitterSupport.inspectCodeStructure(language, source == null ? "" : source);
        Set<String> reported = new LinkedHashSet<>();
        for (CodeSymbol symbol : snapshot.symbols()) {
            if (symbol == null || symbol.bodyInnerRange() == null || !symbol.bodyInnerRange().isValid()) {
                continue;
            }
            String body = sliceUtf8(source, symbol.bodyInnerRange().startByte(), symbol.bodyInnerRange().endByte());
            if (isNoOpBody(body)) {
                String description = symbol.kind() + " " + symbol.name() + " has an empty or no-op body";
                if (reported.add(description)) {
                    findings.add(new ImplementationCompletenessFinding(
                            ImplementationCompletenessFindingType.EMPTY_BEHAVIOR,
                            symbol.name(),
                            description
                    ));
                }
            }
        }

        if (language == SourceLanguage.JAVASCRIPT || language == SourceLanguage.TYPESCRIPT) {
            findings.addAll(javascriptBehaviorScanner.scan(source));
        }
        return new ImplementationBehaviorInspection(findings);
    }

    String normalizeBody(String body) {
        return bodyNormalizer.normalize(body);
    }

    private boolean isNoOpBody(String body) {
        return bodyNormalizer.isNoOpBody(body);
    }

    private String sliceUtf8(String source, int startByte, int endByte) {
        byte[] bytes = (source == null ? "" : source).getBytes(StandardCharsets.UTF_8);
        int start = Math.max(0, Math.min(startByte, bytes.length));
        int end = Math.max(start, Math.min(endByte, bytes.length));
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }

}
