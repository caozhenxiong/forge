package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationJavascriptBehaviorScannerTests {

    @Test
    void findsObjectMemberArrowStub() {
        ImplementationJavascriptBehaviorScanner scanner =
                new ImplementationJavascriptBehaviorScanner(new ImplementationBodyNormalizer());

        List<ImplementationCompletenessFinding> findings = scanner.scan("""
                const handlers = {
                  move: () => {
                  }
                };
                """);

        assertTrue(findings.stream().anyMatch(finding -> "move".equals(finding.symbolName())));
    }

    @Test
    void findsNoOpSwitchHandler() {
        ImplementationJavascriptBehaviorScanner scanner =
                new ImplementationJavascriptBehaviorScanner(new ImplementationBodyNormalizer());

        List<ImplementationCompletenessFinding> findings = scanner.scan("""
                function onKey(event) {
                  switch (event.key) {
                    case 'ArrowLeft':
                      break;
                    case 'ArrowRight':
                      break;
                    default:
                      break;
                  }
                }
                """);

        assertTrue(findings.stream().anyMatch(finding -> finding.evidence().contains("switch handler")));
    }
}
