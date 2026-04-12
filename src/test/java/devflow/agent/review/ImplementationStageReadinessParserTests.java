package devflow.agent.review;

import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ImplementationContinuationMode;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewReasonCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationStageReadinessParserTests {

    private final ImplementationStageReadinessParser parser = new ImplementationStageReadinessParser();

    @Test
    void reportsArchitectFailureWhenPlanIsCompleteButStageNotReady() {
        ImplementationStageReadiness readiness = parser.parse(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                        new ImplementationStageStatusPayload(false, true, false, java.util.List.of())
                )
        );

        assertFalse(readiness.stageReady());
        assertTrue(readiness.summary().contains("整体可运行契约"));
    }

    @Test
    void reportsIncompleteWhenSubtasksRemain() {
        ImplementationStageReadiness readiness = parser.parse(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                        new ImplementationStageStatusPayload(false, false, true, java.util.List.of("CAP-2", "CAP-3"))
                )
        );

        assertFalse(readiness.stageReady());
        assertTrue(readiness.evidence().contains("CAP-2"));
    }

    @Test
    void reportsBlockedWhenImplementationStageDeclaresToolingBlocker() {
        ImplementationStageReadiness readiness = parser.parse(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                        new ImplementationStageStatusPayload(
                                false,
                                false,
                                true,
                                "",
                                "",
                                "",
                                java.util.List.of("补齐接线"),
                                ImplementationContinuationMode.BLOCK_STAGE,
                                "probe invalid",
                                "fix probe contract",
                                "unexpected field bodyTextLength",
                                "wait for human",
                                ImplementationPatchTarget.NONE,
                                ReviewReasonCode.RUNTIME_PROBE_INVALID
                        )
                )
        );

        assertFalse(readiness.stageReady());
        assertEquals(ImplementationContinuationMode.BLOCK_STAGE, readiness.continuationMode());
        assertEquals(ReviewReasonCode.RUNTIME_PROBE_INVALID, readiness.reasonCode());
        assertTrue(readiness.evidence().contains("bodyTextLength"));
    }

    @Test
    void preservesStructuredContinuationForIncompleteStagePatch() {
        ImplementationStageReadiness readiness = parser.parse(
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                        new ImplementationStageStatusPayload(
                                false,
                                false,
                                true,
                                "",
                                "",
                                "",
                                java.util.List.of("补齐接线"),
                                ImplementationContinuationMode.CONTINUE_SUBTASKS,
                                "继续修复当前入口接线",
                                "只修 index.html 与 companion runtime 的接线，不要重做实现。",
                                "continuationSubtask=修接线\nindex.app.js exists but index.html does not reference it",
                                "1. 接入 companion runtime。 2. 保持当前实现骨架。",
                                ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                                ReviewReasonCode.NONE
                        )
                )
        );

        assertFalse(readiness.stageReady());
        assertEquals(ImplementationContinuationMode.CONTINUE_SUBTASKS, readiness.continuationMode());
        assertEquals(ImplementationPatchTarget.PATCH_RUNTIME_WIRING, readiness.implementationPatchTarget());
        assertEquals("继续修复当前入口接线", readiness.summary());
        assertTrue(readiness.changeRequest().contains("只修 index.html"));
        assertTrue(readiness.evidence().contains("continuationSubtask=修接线"));
    }
}
