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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import devflow.agent.executor.implementation.planning.ImplementationOutline;
import devflow.agent.executor.implementation.planning.ImplementationOutlineSubtask;
import devflow.agent.executor.implementation.planning.ImplementationPlanningUnitKind;
class ImplementationPlanningFeedbackRouterTests {

    @Test
    void routesOutlineOwnedIssueWithoutGuessingCodePrefix() {
        ImplementationPlanningFeedbackRouter router = new ImplementationPlanningFeedbackRouter();

        ImplementationPlanningFeedbackRouter.RouteDecision decision = router.route(
                GateReport.failure(
                        "summary",
                        List.of(new GateIssue(
                                "NON_STANDARD_CODE",
                                "outline owned failure",
                                GateFailureDisposition.REPLAN_CURRENT_STAGE,
                                GateIssueContext.forPlanningUnit(
                                        ImplementationPlanningUnitKind.OUTLINE,
                                        "outline"
                                )
                        ))
                ),
                outline()
        );

        assertNotNull(decision);
        assertEquals(ImplementationPlanningUnitKind.OUTLINE, decision.unitKind());
        assertEquals("outline", decision.unitId());
    }

    @Test
    void routesNamedDetailIssueToItsSubtask() {
        ImplementationPlanningFeedbackRouter router = new ImplementationPlanningFeedbackRouter();

        ImplementationPlanningFeedbackRouter.RouteDecision decision = router.route(
                GateReport.failure(
                        "summary",
                        List.of(new GateIssue(
                                "NON_STANDARD_CODE",
                                "detail owned failure",
                                GateFailureDisposition.REPLAN_CURRENT_STAGE,
                                GateIssueContext.forPlanningUnit(
                                        ImplementationPlanningUnitKind.SUBTASK_DETAIL,
                                        "subtask-2"
                                )
                        ))
                ),
                outline()
        );

        assertNotNull(decision);
        assertEquals(ImplementationPlanningUnitKind.SUBTASK_DETAIL, decision.unitKind());
        assertEquals("subtask-2", decision.unitId());
    }

    private ImplementationOutline outline() {
        return new ImplementationOutline(
                "summary",
                List.of(
                        new ImplementationOutlineSubtask(
                                "subtask-1",
                                "first",
                                "goal-1",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                false,
                                DeliveryMode.PATCH,
                                List.of("index.html")
                        ),
                        new ImplementationOutlineSubtask(
                                "subtask-2",
                                "second",
                                "goal-2",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                false,
                                DeliveryMode.PATCH,
                                List.of("src/app.js")
                        )
                )
        );
    }
}
