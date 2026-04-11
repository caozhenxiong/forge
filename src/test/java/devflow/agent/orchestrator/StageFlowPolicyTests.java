package devflow.agent.orchestrator;

import devflow.agent.review.FixMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageFlowPolicyTests {

    private final StageFlowPolicy stageFlowPolicy = new StageFlowPolicy();

    @Test
    void returnsExpectedNextStage() {
        assertEquals(StageType.PRD, stageFlowPolicy.nextStage(StageType.ANALYSIS));
        assertEquals(StageType.DESIGN, stageFlowPolicy.nextStage(StageType.PRD));
        assertEquals(StageType.IMPLEMENTATION, stageFlowPolicy.nextStage(StageType.DESIGN));
        assertEquals(StageType.CODE_REVIEW, stageFlowPolicy.nextStage(StageType.IMPLEMENTATION));
        assertEquals(StageType.TEST, stageFlowPolicy.nextStage(StageType.CODE_REVIEW));
        assertNull(stageFlowPolicy.nextStage(StageType.TEST));
    }

    @Test
    void reroutesDocumentStagesInPlaceAndRuntimeStagesBackToImplementation() {
        assertEquals(StageType.ANALYSIS, stageFlowPolicy.rerouteStage(StageType.ANALYSIS, FixMode.PATCH));
        assertEquals(StageType.PRD, stageFlowPolicy.rerouteStage(StageType.PRD, FixMode.REWORK));
        assertEquals(StageType.DESIGN, stageFlowPolicy.rerouteStage(StageType.DESIGN, FixMode.PATCH));
        assertEquals(StageType.IMPLEMENTATION, stageFlowPolicy.rerouteStage(StageType.IMPLEMENTATION, FixMode.PATCH));
        assertEquals(StageType.IMPLEMENTATION, stageFlowPolicy.rerouteStage(StageType.CODE_REVIEW, FixMode.REWORK));
        assertEquals(StageType.IMPLEMENTATION, stageFlowPolicy.rerouteStage(StageType.TEST, FixMode.PATCH));
    }

    @Test
    void repairRouteBelongsOnlyToImplementationChain() {
        assertFalse(stageFlowPolicy.supportsRepairRoute(StageType.ANALYSIS));
        assertFalse(stageFlowPolicy.supportsRepairRoute(StageType.PRD));
        assertFalse(stageFlowPolicy.supportsRepairRoute(StageType.DESIGN));
        assertTrue(stageFlowPolicy.supportsRepairRoute(StageType.IMPLEMENTATION));
        assertTrue(stageFlowPolicy.supportsRepairRoute(StageType.CODE_REVIEW));
        assertTrue(stageFlowPolicy.supportsRepairRoute(StageType.TEST));
        assertNull(stageFlowPolicy.repairTarget(StageType.PRD));
        assertEquals(StageType.IMPLEMENTATION, stageFlowPolicy.repairTarget(StageType.CODE_REVIEW));
    }
}
