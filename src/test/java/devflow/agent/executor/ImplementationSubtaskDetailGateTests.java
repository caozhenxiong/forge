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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationSubtaskDetailGateTests {

    @Test
    void ignoresOutlineOwnedContinuationRegressions() {
        ImplementationSubtaskDetailGate gate = new ImplementationSubtaskDetailGate();

        GateReport report = gate.evaluate(
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 2, 4, true, false, true, List.of()),
                PlanningRuntimeFacts.empty(),
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

    @Test
    void rejectsRuntimeSplitDetailWithoutHostHtmlPatch() {
        ImplementationSubtaskDetailGate gate = new ImplementationSubtaskDetailGate();

        GateReport report = gate.evaluate(
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 2, 4, true, false, true, List.of()),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.inlineHost(java.nio.file.Path.of("index.html")),
                        List.of(),
                        List.of()
                ),
                new ImplementationOutlineSubtask(
                        "subtask-2",
                        "split runtime",
                        "add runtime companion",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        DeliveryMode.PATCH,
                        List.of("src/engine.js")
                ),
                new ImplementationSubtaskDetail(
                        "subtask-2",
                        List.of(new ImplementationSubtaskDetailChange(
                                "src/engine.js",
                                ChangeAction.WRITE,
                                "add runtime companion",
                                PlanningRuntimeScriptRole.ROOT
                        ))
                )
        );

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("宿主 HTML patch")));
    }

    @Test
    void rejectsNewRuntimeScriptWithoutExplicitRuntimeRole() {
        ImplementationSubtaskDetailGate gate = new ImplementationSubtaskDetailGate();

        GateReport report = gate.evaluate(
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 3, 4, true, false, true, List.of()),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.externalCompanion(
                                java.nio.file.Path.of("index.html"),
                                List.of(java.nio.file.Path.of("index.app.js"))
                        ),
                        List.of(java.nio.file.Path.of("index.app.js")),
                        List.of(java.nio.file.Path.of("index.app.js"))
                ),
                new ImplementationOutlineSubtask(
                        "subtask-3",
                        "extend runtime",
                        "patch existing runtime and add module",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        DeliveryMode.PATCH,
                        List.of("index.app.js", "src/engine.js")
                ),
                new ImplementationSubtaskDetail(
                        "subtask-3",
                        List.of(
                                new ImplementationSubtaskDetailChange(
                                        "index.app.js",
                                        ChangeAction.WRITE,
                                        "extend current runtime"
                                ),
                                new ImplementationSubtaskDetailChange(
                                        "src/engine.js",
                                        ChangeAction.WRITE,
                                        "add new module"
                                )
                        )
                )
        );

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("runtimeScriptRole=ROOT|LEAF")));
    }

    @Test
    void acceptsNewLeafRuntimeScriptWhenRoleIsExplicit() {
        ImplementationSubtaskDetailGate gate = new ImplementationSubtaskDetailGate();

        GateReport report = gate.evaluate(
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 3, 4, true, false, true, List.of()),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.externalCompanion(
                                java.nio.file.Path.of("index.html"),
                                List.of(java.nio.file.Path.of("index.app.js"))
                        ),
                        List.of(java.nio.file.Path.of("index.app.js")),
                        List.of(java.nio.file.Path.of("index.app.js"))
                ),
                new ImplementationOutlineSubtask(
                        "subtask-4",
                        "extend runtime",
                        "patch existing runtime and add module",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        DeliveryMode.PATCH,
                        List.of("index.app.js", "src/engine.js")
                ),
                new ImplementationSubtaskDetail(
                        "subtask-4",
                        List.of(
                                new ImplementationSubtaskDetailChange(
                                        "index.app.js",
                                        ChangeAction.WRITE,
                                        "extend current runtime"
                                ),
                                new ImplementationSubtaskDetailChange(
                                        "src/engine.js",
                                        ChangeAction.WRITE,
                                        "add new module",
                                        PlanningRuntimeScriptRole.LEAF
                                )
                        )
                )
        );

        assertTrue(report.passed(), report.issues().toString());
    }

    @Test
    void rejectsRuntimeRoleOnHostHtmlEntry() {
        ImplementationSubtaskDetailGate gate = new ImplementationSubtaskDetailGate();

        GateReport report = gate.evaluate(
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 2, 4, true, false, true, List.of()),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.inlineHost(java.nio.file.Path.of("index.html")),
                        List.of(),
                        List.of()
                ),
                new ImplementationOutlineSubtask(
                        "subtask-6",
                        "patch host",
                        "update host html",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        true,
                        DeliveryMode.PATCH,
                        List.of("index.html")
                ),
                new ImplementationSubtaskDetail(
                        "subtask-6",
                        List.of(
                                new ImplementationSubtaskDetailChange(
                                        "index.html",
                                        ChangeAction.WRITE,
                                        "patch host html",
                                        PlanningRuntimeScriptRole.ROOT
                                )
                        )
                )
        );

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("runtimeScriptRole 只能用于当前 HTML 入口树下的新增 runtime 脚本")));
    }

    @Test
    void rejectsRuntimeRoleOnNonRuntimeFile() {
        ImplementationSubtaskDetailGate gate = new ImplementationSubtaskDetailGate();

        GateReport report = gate.evaluate(
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 3, 4, true, false, true, List.of()),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.externalCompanion(
                                java.nio.file.Path.of("index.html"),
                                List.of(java.nio.file.Path.of("index.app.js"))
                        ),
                        List.of(java.nio.file.Path.of("index.app.js")),
                        List.of(java.nio.file.Path.of("index.app.js"))
                ),
                new ImplementationOutlineSubtask(
                        "subtask-7",
                        "patch style",
                        "update style file",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        DeliveryMode.PATCH,
                        List.of("styles/app.css")
                ),
                new ImplementationSubtaskDetail(
                        "subtask-7",
                        List.of(
                                new ImplementationSubtaskDetailChange(
                                        "styles/app.css",
                                        ChangeAction.WRITE,
                                        "tweak styles",
                                        PlanningRuntimeScriptRole.LEAF
                                )
                        )
                )
        );

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("runtimeScriptRole 只能用于当前 HTML 入口树下的新增 runtime 脚本")));
    }

    @Test
    void rejectsRuntimeRoleOnReachableRuntimeScript() {
        ImplementationSubtaskDetailGate gate = new ImplementationSubtaskDetailGate();

        GateReport report = gate.evaluate(
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 3, 4, true, false, true, List.of()),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.externalCompanion(
                                java.nio.file.Path.of("index.html"),
                                List.of(java.nio.file.Path.of("index.app.js"))
                        ),
                        List.of(java.nio.file.Path.of("index.app.js")),
                        List.of(java.nio.file.Path.of("index.app.js"))
                ),
                new ImplementationOutlineSubtask(
                        "subtask-8",
                        "patch runtime",
                        "update current runtime",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        DeliveryMode.PATCH,
                        List.of("index.app.js")
                ),
                new ImplementationSubtaskDetail(
                        "subtask-8",
                        List.of(
                                new ImplementationSubtaskDetailChange(
                                        "index.app.js",
                                        ChangeAction.WRITE,
                                        "extend current runtime",
                                        PlanningRuntimeScriptRole.LEAF
                                )
                        )
                )
        );

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("只有新增 runtime 脚本才允许声明角色")));
    }

    @Test
    void allowsDeletingRuntimeScriptWithoutDeclaringRuntimeRole() {
        ImplementationSubtaskDetailGate gate = new ImplementationSubtaskDetailGate();

        GateReport report = gate.evaluate(
                new DeliveryPolicyEnvelope(DeliveryMode.PATCH, 3, 4, true, false, true, List.of()),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.externalCompanion(
                                java.nio.file.Path.of("index.html"),
                                List.of(java.nio.file.Path.of("index.app.js"))
                        ),
                        List.of(java.nio.file.Path.of("index.app.js")),
                        List.of(java.nio.file.Path.of("index.app.js"), java.nio.file.Path.of("admin.app.js"))
                ),
                new ImplementationOutlineSubtask(
                        "subtask-5",
                        "remove orphan runtime",
                        "delete an unused runtime root",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        DeliveryMode.PATCH,
                        List.of("admin.app.js")
                ),
                new ImplementationSubtaskDetail(
                        "subtask-5",
                        List.of(
                                new ImplementationSubtaskDetailChange(
                                        "admin.app.js",
                                        ChangeAction.DELETE,
                                        "remove orphan runtime"
                                )
                        )
                )
        );

        assertTrue(report.passed(), report.issues().toString());
    }
}
