package devflow.agent.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.DeliveryPolicyEnvelope;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.gate.ArchitectIntegrationCheckResult;
import devflow.agent.executor.gate.ArchitectIntegrationCheckScope;
import devflow.agent.executor.gate.ArchitectIntegrationFailureReason;
import devflow.agent.executor.gate.ImplementationStageStatus;
import devflow.agent.executor.implementation.planning.ImplementationPlan;
import devflow.agent.executor.implementation.state.ImplementationRuntimeSnapshot;
import devflow.agent.executor.implementation.state.ImplementationStateArtifactSupport;
import devflow.agent.executor.implementation.state.ImplementationStateSnapshotSerializer;
import devflow.agent.executor.runtime.HtmlRuntimeOwnershipContract;
import devflow.agent.executor.runtime.RuntimeOwnershipMode;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import devflow.agent.executor.subtask.Subtask;

class ImplementationContinuationSupportTests {

    @Test
    void roundTripsCanonicalContinuationPatchThroughImplementationStatePayload() {
        HtmlRuntimeOwnershipContract runtimeContract = HtmlRuntimeOwnershipContract.externalCompanion(
                Path.of("index.html"),
                List.of(Path.of("index.app.js"))
        );
        List<FileChange> canonicalRepairPackage = new devflow.agent.executor.runtime.RuntimeWiringRetryChangeFactory()
                .build(runtimeContract);
        ImplementationStageStatus stageStatus = new ImplementationStageStatus(
                1,
                1,
                0,
                false,
                false,
                List.of("修接线"),
                ArchitectIntegrationCheckResult.failure(
                        ArchitectIntegrationCheckScope.RUNNABLE_MILESTONE,
                        ArchitectIntegrationFailureReason.RUNTIME_WIRING_INVALID,
                        "index.app.js exists but index.html does not reference it",
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                        runtimeContract
                ),
                ImplementationContinuationMode.CONTINUE_SUBTASKS,
                "继续修当前入口接线",
                "只修宿主 HTML 与 companion runtime 的接线。",
                "continuationSubtask=修接线\nindex.app.js exists but index.html does not reference it",
                "1. 引入 companion runtime。 2. 不要重做业务逻辑。",
                canonicalRepairPackage,
                ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                ReviewReasonCode.RUNTIME_WIRING_GAP
        );
        ImplementationRuntimeSnapshot snapshot = new ImplementationRuntimeSnapshot(
                new ImplementationPlan("修复接线", List.of(new Subtask(
                        "修接线",
                        "修复宿主 HTML 与 companion runtime 接线",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("入口接线正确"),
                        false,
                        DeliveryMode.PATCH,
                        canonicalRepairPackage
                ))),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                DocumentLanguage.ZH,
                DeliveryPolicyEnvelope.defaultPolicy(),
                null,
                stageStatus,
                "修接线"
        );

        String stateJson = new ImplementationStateSnapshotSerializer(new ObjectMapper()).renderStateJson(snapshot);
        ImplementationStageStatusPayload payload = new ImplementationStateArtifactSupport().readStageStatus(stateJson);
        StageContinuationContext continuationContext = new ImplementationContinuationSupport().toContinuationContext(payload);

        assertEquals(ImplementationContinuationMode.CONTINUE_SUBTASKS, payload.continuationMode());
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING, continuationContext.implementationPatchTarget());
        assertEquals(ReviewReasonCode.RUNTIME_WIRING_GAP, continuationContext.reasonCode());
        assertEquals(List.of("index.html", "index.app.js"),
                continuationContext.overrideChanges().stream().map(FileChange::path).toList());
        assertEquals(ChangeAction.WRITE, continuationContext.overrideChanges().getFirst().action());
        assertEquals(RuntimeOwnershipMode.EXTERNAL_COMPANION,
                continuationContext.overrideChanges().getFirst().runtimeOwnership());
    }
}
