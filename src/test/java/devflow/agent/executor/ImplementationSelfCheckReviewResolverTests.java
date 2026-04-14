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

import devflow.agent.executor.subtask.ImplementationSelfCheckReviewResolver;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolName;
import devflow.agent.executor.tools.ToolResult;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewRevisionRoute;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImplementationSelfCheckReviewResolverTests {

    private final ImplementationSelfCheckReviewResolver resolver = new ImplementationSelfCheckReviewResolver();

    @Test
    void returnsNullWhenSelfCheckPassed() {
        assertNull(resolver.resolve(
                new SelfCheckResult(true, "ok", ""),
                List.of(ToolResult.success(ToolName.RESOURCE_LINK_VERIFY)),
                DocumentLanguage.ZH
        ));
    }

    @Test
    void routesProbeFailuresToHumanInsteadOfRetryingImplementation() {
        var review = resolver.resolve(
                new SelfCheckResult(false, "probe failed", "payload invalid"),
                List.of(ToolResult.failure(
                        ToolName.PLAYWRIGHT_SMOKE,
                        ToolFailureCode.PLAYWRIGHT_PROBE_PAYLOAD_INVALID,
                        "unexpected field bodyTextLength",
                        "fix probe contract"
                )),
                DocumentLanguage.ZH
        );

        assertEquals(ReviewRevisionRoute.REQUEST_HUMAN, review.revisionRoute());
        assertEquals(ReviewReasonCode.RUNTIME_PROBE_INVALID, review.reasonCode());
        assertEquals(ImplementationPatchTarget.NONE, review.implementationPatchTarget());
    }

    @Test
    void mapsRuntimeWiringFailureToRuntimePatchTarget() {
        var review = resolver.resolve(
                new SelfCheckResult(false, "wiring failed", "runtime not wired"),
                List.of(ToolResult.failure(
                        ToolName.RUNTIME_WIRING_VERIFY,
                        ToolFailureCode.RUNTIME_WIRING_INVALID,
                        "index.app.js exists but index.html does not load it",
                        "wire runtime"
                )),
                DocumentLanguage.ZH
        );

        assertEquals(ReviewRevisionRoute.PATCH_CURRENT_STAGE, review.revisionRoute());
        assertEquals(ReviewReasonCode.RUNTIME_WIRING_GAP, review.reasonCode());
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING, review.implementationPatchTarget());
    }
}
