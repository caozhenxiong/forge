package devflow.agent.protocol;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionDirectiveFeedbackSupportTests {

    @Test
    void mergeKeepsFreshConcretePatchPackageForActiveRetryFeedback() {
        String persistent = ExecutionDirectiveNarrativeRenderer.renderRevisionNote(
                new ExecutionDirectivePayload(
                        FixMode.PATCH.name(),
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION.name(),
                        List.of(new FileChangePayload("index.html", ChangeAction.WRITE.name(), "repair host", "HOST_HTML_PATCH", null, true)),
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
                        "继续修 host",
                        "只修 host html",
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
                ),
                "继续修 host",
                "只修 host html",
                "evidence",
                "action"
        );

        String transientFeedback = ExecutionDirectiveNarrativeRenderer.renderRetryFeedback(
                new ExecutionDirectivePayload(
                        FixMode.PATCH.name(),
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION.name(),
                        List.of(new FileChangePayload("index.html", ChangeAction.WRITE.name(), "repair host", "HOST_HTML_PATCH", null, true)),
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
                        "仍需修 host",
                        "继续修",
                        "runtime gap",
                        "action",
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null
                ),
                "self check",
                "details",
                "summary",
                "changeRequest",
                "completeness",
                "- evidence"
        );

        String merged = ExecutionDirectiveFeedbackSupport.merge(persistent, transientFeedback);
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(merged);

        assertTrue(ExecutionDirectiveFeedbackSupport.carriesConcretePatchPackage(merged));
        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION.name(), directives.implementationPatchTarget());
        assertEquals(1, directives.overrideChanges().size());
        assertEquals("index.html", directives.overrideChanges().getFirst().path());
        assertEquals("仍需修 host", directives.summary());
        assertEquals("继续修", directives.changeRequest());
    }

    @Test
    void mergeDoesNotReviveBaseConcretePatchPackageWhenFreshFeedbackLacksConcreteScope() {
        String persistent = ExecutionDirectiveNarrativeRenderer.renderRevisionNote(
                new ExecutionDirectivePayload(
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
                ),
                "继续修 gameplay",
                "只修当前 patch scope",
                "evidence",
                "action"
        );

        String transientFeedback = ExecutionDirectiveNarrativeRenderer.renderRetryFeedback(
                new ExecutionDirectivePayload(
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
                        "继续观察 runtime wiring",
                        "暂时不要清空既有 patch scope",
                        "no structured runtime scope yet",
                        "wait",
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null
                ),
                "self check",
                "details",
                "summary",
                "changeRequest",
                "completeness",
                "- evidence"
        );

        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(
                ExecutionDirectiveFeedbackSupport.merge(persistent, transientFeedback)
        );

        assertFalse(ExecutionDirectiveFeedbackSupport.carriesConcretePatchPackage(
                ExecutionDirectiveFeedbackSupport.merge(persistent, transientFeedback)
        ));
        assertTrue(directives.overrideChanges().isEmpty());
        assertEquals("继续观察 runtime wiring", directives.summary());
    }

    @Test
    void persistentRetryFeedbackPreservesConcretePatchPackageForActiveRetry() {
        String note = ExecutionDirectiveNarrativeRenderer.renderRevisionNote(
                new ExecutionDirectivePayload(
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
                        "只修 host 和 companion 接线",
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
                ),
                "继续修接线",
                "只修 host 和 companion 接线",
                "evidence",
                "action"
        );

        String persistent = ExecutionDirectiveFeedbackSupport.persistentRetryFeedback(note);
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(persistent);

        assertTrue(ExecutionDirectiveFeedbackSupport.carriesConcretePatchPackage(persistent));
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING.name(), directives.implementationPatchTarget());
        assertEquals(2, directives.overrideChanges().size());
    }

    @Test
    void persistentRetryFeedbackDropsPureProseNote() {
        assertFalse(ExecutionDirectiveFeedbackSupport.carriesConcretePatchPackage("plain prose only"));
        assertTrue(ExecutionDirectiveFeedbackSupport.persistentRetryFeedback("plain prose only").isBlank());
    }

    @Test
    void repairBriefFeedbackStripsConcretePatchPackageFromEnforcedRepairBrief() {
        String note = ExecutionDirectiveNarrativeRenderer.renderRevisionNote(
                new ExecutionDirectivePayload(
                        FixMode.PATCH.name(),
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION.name(),
                        List.of(new FileChangePayload("src/game.js", ChangeAction.WRITE.name(), "repair gameplay", "AUTO", null, false)),
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("runtime proof"),
                        List.of("fix gameplay"),
                        List.of("不要重写入口"),
                        List.of("玩法可运行"),
                        List.of(),
                        List.of(),
                        "继续修 gameplay",
                        "只修当前 gameplay scope",
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
                ),
                "继续修 gameplay",
                "只修当前 gameplay scope",
                "evidence",
                "action"
        );

        String repairBrief = ExecutionDirectiveFeedbackSupport.repairBriefFeedback(note);
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(repairBrief);

        assertFalse(ExecutionDirectiveFeedbackSupport.carriesConcretePatchPackage(repairBrief));
        assertTrue(Boolean.TRUE.equals(directives.repairBriefEnforced()));
        assertEquals(0, directives.overrideChanges().size());
        assertEquals(List.of("fix gameplay"), directives.mustFixFirst());
        assertEquals(List.of("不要重写入口"), directives.forbiddenDirections());
    }
}
