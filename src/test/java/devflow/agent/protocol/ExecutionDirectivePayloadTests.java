package devflow.agent.protocol;

import devflow.agent.executor.ChangeAction;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionDirectivePayloadTests {

    @Test
    void mergeDoesNotReviveBaseConcretePatchPackageWhenOverrideHasNoConcreteScope() {
        ExecutionDirectivePayload base = new ExecutionDirectivePayload(
                FixMode.PATCH.name(),
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION.name(),
                List.of(new FileChangePayload("src/game.js", ChangeAction.WRITE.name(), "repair gameplay", "AUTO", null, false)),
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "继续修 gameplay",
                "只修当前 patch scope",
                "evidence",
                "action",
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null
        );
        ExecutionDirectivePayload override = new ExecutionDirectivePayload(
                FixMode.PATCH.name(),
                null,
                List.of(),
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "继续观察",
                "暂不扩大 patch scope",
                "fresh feedback has no concrete scope",
                "wait",
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null
        );

        ExecutionDirectivePayload merged = base.merge(override);

        assertEquals(null, merged.implementationPatchTarget());
        assertTrue(merged.overrideChanges().isEmpty());
        assertEquals("继续观察", merged.summary());
    }

    @Test
    void mergePrefersFreshConcretePatchPackageOverBasePackage() {
        ExecutionDirectivePayload base = new ExecutionDirectivePayload(
                FixMode.PATCH.name(),
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION.name(),
                List.of(new FileChangePayload("src/game.js", ChangeAction.WRITE.name(), "repair gameplay", "AUTO", null, false)),
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "继续修 gameplay",
                "只修 gameplay",
                "evidence",
                "action",
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null
        );
        ExecutionDirectivePayload override = new ExecutionDirectivePayload(
                FixMode.PATCH.name(),
                ImplementationPatchTarget.PATCH_RUNTIME_WIRING.name(),
                List.of(
                        new FileChangePayload("index.html", ChangeAction.WRITE.name(), "wire host", "HOST_HTML_PATCH", "EXTERNAL_COMPANION", true),
                        new FileChangePayload("index.app.js", ChangeAction.WRITE.name(), "wire companion", "AUTO", "EXTERNAL_COMPANION", false)
                ),
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "继续修接线",
                "只修 host 和 companion",
                "evidence",
                "action",
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null
        );

        ExecutionDirectivePayload merged = base.merge(override);

        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING.name(), merged.implementationPatchTarget());
        assertEquals(2, merged.overrideChanges().size());
        assertEquals("index.html", merged.overrideChanges().getFirst().path());
        assertEquals("继续修接线", merged.summary());
    }
}
