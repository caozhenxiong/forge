package devflow.agent.executor;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaScript/TypeScript 行为特征扫描器。
 *
 * <p>负责 tree-sitter 之外的轻量启发式补充扫描，例如对象成员空函数、
 * no-op switch 分支等。这些规则只输出结构化 finding，不参与阻塞决策。
 */
final class ImplementationJavascriptBehaviorScanner {

    private final JavascriptPropertyStubScanner propertyStubScanner;
    private final JavascriptNoOpSwitchScanner noOpSwitchScanner;

    ImplementationJavascriptBehaviorScanner(ImplementationBodyNormalizer bodyNormalizer) {
        JavascriptScannerSupport scannerSupport = new JavascriptScannerSupport();
        this.propertyStubScanner = new JavascriptPropertyStubScanner(bodyNormalizer, scannerSupport);
        this.noOpSwitchScanner = new JavascriptNoOpSwitchScanner(bodyNormalizer, scannerSupport);
    }

    List<ImplementationCompletenessFinding> scan(String source) {
        List<ImplementationCompletenessFinding> findings = new ArrayList<>();
        findings.addAll(propertyStubScanner.scan(source));
        findings.addAll(noOpSwitchScanner.scan(source));
        return findings;
    }
}
