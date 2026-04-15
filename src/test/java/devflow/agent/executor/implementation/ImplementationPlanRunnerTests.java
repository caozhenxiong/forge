package devflow.agent.executor.implementation;

import devflow.agent.domain.RunRecord;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.DeliveryPolicyEnvelope;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskAttemptReport;
import devflow.agent.executor.subtask.SubtaskExecutionContext;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
import devflow.agent.executor.subtask.SubtaskExecutionState;
import devflow.agent.executor.subtask.SubtaskExecutor;
import devflow.agent.executor.subtask.TaskPackage;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.protocol.ExecutionDirectiveFeedbackSupport;
import devflow.agent.protocol.ExecutionDirectiveNarrativeRenderer;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationPlanRunnerTests {

    @Test
    void passesConcreteContinuationNoteAsPersistentRetryFeedbackToFirstActiveSubtask() {
        AtomicReference<SubtaskExecutionContext> captured = new AtomicReference<>();
        SubtaskExecutor stubExecutor = new SubtaskExecutor(null, null, null, 1) {
            @Override
            public SubtaskExecutionReport executeSubtask(SubtaskExecutionContext executionContext) {
                captured.set(executionContext);
                return new SubtaskExecutionReport(
                        executionContext.subtask(),
                        false,
                        List.of(SubtaskAttemptReport.fromVerification(
                                1,
                                new devflow.agent.executor.SelfCheckResult(false, "failed", "details"),
                                List.of(),
                                new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "needs patch", "patch it")
                        )),
                        new SubtaskExecutionState(DeliveryMode.PATCH, true)
                );
            }
        };
        ImplementationPlanRunner runner = new ImplementationPlanRunner(stubExecutor);
        Subtask subtask = new Subtask(
                "修接线",
                "修复入口与 companion runtime 接线",
                List.of(),
                List.of(),
                List.of(),
                List.of("入口可运行"),
                false,
                DeliveryMode.PATCH,
                List.of(
                        new FileChange("index.html", ChangeAction.WRITE, "wire host"),
                        new FileChange("index.app.js", ChangeAction.WRITE, "wire companion")
                )
        );
        String note = ExecutionDirectiveNarrativeRenderer.renderRevisionNote(
                new ExecutionDirectivePayload(
                        FixMode.PATCH.name(),
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING.name(),
                        List.of(
                                new devflow.agent.protocol.FileChangePayload("index.html", ChangeAction.WRITE.name(), "wire host", "HOST_HTML_PATCH", "EXTERNAL_COMPANION", true),
                                new devflow.agent.protocol.FileChangePayload("index.app.js", ChangeAction.WRITE.name(), "wire companion", "AUTO", "EXTERNAL_COMPANION", false)
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
                        "只修 host 与 companion runtime 接线",
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
                "只修 host 与 companion runtime 接线",
                "evidence",
                "action"
        );

        runner.execute(
                Path.of("."),
                (RunRecord) null,
                note,
                new devflow.agent.executor.implementation.planning.ImplementationPlan("summary", List.of(subtask)),
                List.of(new TaskPackage(
                        subtask.title(),
                        subtask.goal(),
                        subtask.deliveryMode().name(),
                        false,
                        List.of("index.html", "index.app.js"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("入口可运行"),
                        List.of(),
                        List.of(),
                        "",
                        null
                )),
                DeliveryPolicyEnvelope.defaultPolicy(),
                null,
                null,
                null,
                DocumentLanguage.ZH,
                List.of(),
                new SubtaskExecutionState(DeliveryMode.PATCH, true),
                null,
                "",
                null,
                null
        );

        SubtaskExecutionContext executionContext = captured.get();
        ExecutionDirectivePayload persistent = ExecutionDirectiveProtocol.parseMerged(executionContext.persistentRepairFeedback());
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING.name(), persistent.implementationPatchTarget());
        assertEquals(2, persistent.overrideChanges().size());
        assertTrue(executionContext.inheritedFeedback().contains("继续修接线"));
    }

    @Test
    void doesNotLeakConcretePatchPersistenceToLaterSubtasks() {
        List<SubtaskExecutionContext> captured = new ArrayList<>();
        SubtaskExecutor stubExecutor = new SubtaskExecutor(null, null, null, 1) {
            @Override
            public SubtaskExecutionReport executeSubtask(SubtaskExecutionContext executionContext) {
                captured.add(executionContext);
                boolean completed = captured.size() == 1;
                return new SubtaskExecutionReport(
                        executionContext.subtask(),
                        completed,
                        List.of(SubtaskAttemptReport.fromVerification(
                                1,
                                new devflow.agent.executor.SelfCheckResult(completed, completed ? "ok" : "failed", "details"),
                                List.of(),
                                new ReviewResult(
                                        completed ? ReviewDecision.APPROVED : ReviewDecision.REVISION_REQUIRED,
                                        completed ? FixMode.NONE : FixMode.PATCH,
                                        completed ? "approved" : "needs patch",
                                        completed ? "" : "patch it"
                                )
                        )),
                        new SubtaskExecutionState(executionContext.subtask().deliveryMode(), true)
                );
            }
        };
        ImplementationPlanRunner runner = new ImplementationPlanRunner(stubExecutor);
        String note = ExecutionDirectiveNarrativeRenderer.renderRevisionNote(
                new ExecutionDirectivePayload(
                        FixMode.PATCH.name(),
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION.name(),
                        List.of(new devflow.agent.protocol.FileChangePayload("src/game.js", ChangeAction.WRITE.name(), "repair gameplay", "AUTO", null, false)),
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

        runner.execute(
                Path.of("."),
                (RunRecord) null,
                note,
                new devflow.agent.executor.implementation.planning.ImplementationPlan(
                        "summary",
                        List.of(
                                new Subtask("先修 gameplay", "repair gameplay", List.of(), List.of(), List.of(), List.of(), false, DeliveryMode.PATCH, List.of(new FileChange("src/game.js", ChangeAction.WRITE, "repair gameplay"))),
                                new Subtask("后续任务", "follow-up", List.of(), List.of(), List.of(), List.of(), false, DeliveryMode.PATCH, List.of(new FileChange("src/ui.js", ChangeAction.WRITE, "follow-up")))
                        )
                ),
                List.of(
                        new TaskPackage("先修 gameplay", "repair gameplay", DeliveryMode.PATCH.name(), false, List.of("src/game.js"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", null),
                        new TaskPackage("后续任务", "follow-up", DeliveryMode.PATCH.name(), false, List.of("src/ui.js"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", null)
                ),
                DeliveryPolicyEnvelope.defaultPolicy(),
                null,
                null,
                null,
                DocumentLanguage.ZH,
                List.of(),
                new SubtaskExecutionState(DeliveryMode.PATCH, true),
                null,
                "",
                null,
                null
        );

        assertEquals(2, captured.size());
        assertEquals(1, ExecutionDirectiveProtocol.parseMerged(captured.getFirst().persistentRepairFeedback()).overrideChanges().size());
        assertTrue(captured.get(1).persistentRepairFeedback().isBlank());
    }

    @Test
    void keepsRepairBriefConstraintsButDropsConcretePatchPackageForLaterSubtasks() {
        List<SubtaskExecutionContext> captured = new ArrayList<>();
        SubtaskExecutor stubExecutor = new SubtaskExecutor(null, null, null, 1) {
            @Override
            public SubtaskExecutionReport executeSubtask(SubtaskExecutionContext executionContext) {
                captured.add(executionContext);
                boolean completed = captured.size() == 1;
                return new SubtaskExecutionReport(
                        executionContext.subtask(),
                        completed,
                        List.of(SubtaskAttemptReport.fromVerification(
                                1,
                                new devflow.agent.executor.SelfCheckResult(completed, completed ? "ok" : "failed", "details"),
                                List.of(),
                                new ReviewResult(
                                        completed ? ReviewDecision.APPROVED : ReviewDecision.REVISION_REQUIRED,
                                        completed ? FixMode.NONE : FixMode.PATCH,
                                        completed ? "approved" : "needs patch",
                                        completed ? "" : "patch it"
                                )
                        )),
                        new SubtaskExecutionState(executionContext.subtask().deliveryMode(), true)
                );
            }
        };
        ImplementationPlanRunner runner = new ImplementationPlanRunner(stubExecutor);
        String note = ExecutionDirectiveNarrativeRenderer.renderRevisionNote(
                new ExecutionDirectivePayload(
                        FixMode.PATCH.name(),
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION.name(),
                        List.of(new devflow.agent.protocol.FileChangePayload("src/game.js", ChangeAction.WRITE.name(), "repair gameplay", "AUTO", null, false)),
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of("fix gameplay"),
                        List.of("不要重写入口"),
                        List.of("玩法可运行"),
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

        runner.execute(
                Path.of("."),
                (RunRecord) null,
                note,
                new devflow.agent.executor.implementation.planning.ImplementationPlan(
                        "summary",
                        List.of(
                                new Subtask("先修 gameplay", "repair gameplay", List.of(), List.of(), List.of(), List.of(), false, DeliveryMode.PATCH, List.of(new FileChange("src/game.js", ChangeAction.WRITE, "repair gameplay"))),
                                new Subtask("后续任务", "follow-up", List.of(), List.of(), List.of(), List.of(), false, DeliveryMode.PATCH, List.of(new FileChange("src/ui.js", ChangeAction.WRITE, "follow-up")))
                        )
                ),
                List.of(
                        new TaskPackage("先修 gameplay", "repair gameplay", DeliveryMode.PATCH.name(), false, List.of("src/game.js"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", null),
                        new TaskPackage("后续任务", "follow-up", DeliveryMode.PATCH.name(), false, List.of("src/ui.js"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", null)
                ),
                DeliveryPolicyEnvelope.defaultPolicy(),
                null,
                null,
                null,
                DocumentLanguage.ZH,
                List.of(),
                new SubtaskExecutionState(DeliveryMode.PATCH, true),
                null,
                "",
                null,
                null
        );

        assertEquals(2, captured.size());
        assertTrue(ExecutionDirectiveFeedbackSupport.carriesConcretePatchPackage(captured.getFirst().persistentRepairFeedback()));
        ExecutionDirectivePayload secondPersistent = ExecutionDirectiveProtocol.parseMerged(captured.get(1).persistentRepairFeedback());
        assertFalse(ExecutionDirectiveFeedbackSupport.carriesConcretePatchPackage(captured.get(1).persistentRepairFeedback()));
        assertTrue(Boolean.TRUE.equals(secondPersistent.repairBriefEnforced()));
        assertEquals(List.of("fix gameplay"), secondPersistent.mustFixFirst());
        assertEquals(List.of("不要重写入口"), secondPersistent.forbiddenDirections());
        assertTrue(secondPersistent.overrideChanges().isEmpty());
    }
}
