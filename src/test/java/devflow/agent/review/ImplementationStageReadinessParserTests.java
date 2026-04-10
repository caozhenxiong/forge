package devflow.agent.review;

import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
