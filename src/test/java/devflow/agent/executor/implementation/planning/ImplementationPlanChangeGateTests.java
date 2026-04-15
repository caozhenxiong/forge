package devflow.agent.executor.implementation.planning;

import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.gate.GateIssue;
import devflow.agent.executor.runtime.HtmlRuntimeOwnershipContract;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationPlanChangeGateTests {

    @Test
    void outlineDoesNotTreatBrandNewRuntimeRootAsKnownRootFact() {
        ImplementationPlanChangeGate gate = new ImplementationPlanChangeGate();

        List<GateIssue> issues = gate.evaluateOutlineContinuationConstraints(
                new PlanningRuntimeFacts(
                        Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.inlineHost(Path.of("index.html")),
                        List.of(),
                        List.of()
                ),
                ImplementationContinuationConstraints.empty(),
                List.of(new ImplementationOutlineSubtask(
                        "outline-1",
                        "新增 runtime root",
                        "先把 brand-new root 下沉到 detail 再做角色校验",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        DeliveryMode.PATCH,
                        List.of("src/engine.js")
                ))
        );

        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void outlineStillBlocksKnownRuntimeRootWithoutHostPatch() {
        ImplementationPlanChangeGate gate = new ImplementationPlanChangeGate();

        List<GateIssue> issues = gate.evaluateOutlineContinuationConstraints(
                new PlanningRuntimeFacts(
                        Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.externalCompanion(
                                Path.of("index.html"),
                                List.of(Path.of("index.app.js"))
                        ),
                        List.of(Path.of("index.app.js")),
                        List.of(Path.of("index.app.js"), Path.of("admin.app.js"))
                ),
                ImplementationContinuationConstraints.empty(),
                List.of(new ImplementationOutlineSubtask(
                        "outline-2",
                        "改 known orphan root",
                        "当前 outline 仍然不能把已知 root 拆成无 host patch package",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        DeliveryMode.PATCH,
                        List.of("admin.app.js")
                ))
        );

        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("宿主 HTML patch")));
    }
}
