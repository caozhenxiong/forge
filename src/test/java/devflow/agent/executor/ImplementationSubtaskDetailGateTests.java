package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationSubtaskDetailGateTests {

    @Test
    void ignoresOutlineOwnedContinuationRegressions() {
        ImplementationSubtaskDetailGate gate = new ImplementationSubtaskDetailGate();

        GateReport report = gate.evaluate(
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 2, 4, true, false, true, List.of()),
                new ImplementationOutlineSubtask(
                        "subtask-1",
                        "repair host html",
                        "fix runtime wiring",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        true,
                        DeliveryMode.REWORK,
                        List.of("index.html")
                ),
                new ImplementationSubtaskDetail(
                        "subtask-1",
                        List.of(new ImplementationSubtaskDetailChange(
                                "index.html",
                                ChangeAction.WRITE,
                                "repair host html wiring"
                        ))
                )
        );

        assertTrue(report.passed(), report.issues().toString());
    }
}
