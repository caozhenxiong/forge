package devflow.agent.orchestrator;

import devflow.agent.executor.FileChange;
import devflow.agent.protocol.ExecutionDirectiveNarrativeRenderer;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.FileChangePayload;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import java.util.List;

/**
 * implementation continuation note 的唯一构造器。
 */
final class StageContinuationNoteBuilder {

    String build(StageContinuationContext context) {
        return ExecutionDirectiveNarrativeRenderer.renderRevisionNote(
                new ExecutionDirectivePayload(
                        FixMode.PATCH.name(),
                        context.implementationPatchTarget() == null
                                ? ImplementationPatchTarget.NONE.name()
                                : context.implementationPatchTarget().name(),
                        context.overrideChanges().stream().map(this::toPayload).toList(),
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
                        context.summary(),
                        context.changeRequest(),
                        context.evidence(),
                        context.actionItems(),
                        null,
                        "继续当前 implementation 阶段，收敛未完成的问题。",
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null
                ),
                context.summary(),
                context.changeRequest(),
                context.evidence(),
                context.actionItems()
        );
    }

    private FileChangePayload toPayload(FileChange change) {
        return new FileChangePayload(
                change.path(),
                change.action() == null ? null : change.action().name(),
                change.reason() == null ? "" : change.reason(),
                change.effectiveEditScope().name(),
                change.runtimeOwnership() == null ? null : change.runtimeOwnership().name(),
                change.hostHtmlPatchRequired()
        );
    }
}
