package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.toolloop.*;

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
