package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ProductContract;
import devflow.agent.validation.ProjectFingerprint;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationPlanGateTests {

    @Test
    void succeedsWhenHtmlEntryDeclaresStableInlineOwnership() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html"), List.of()),
                contractView(),
                List.of("index.html", "game.js"),
                List.of("创建网页入口", "补齐逻辑"),
                List.of("CAP-1", "CAP-2"),
                List.of("SKELETON", "INCREMENTAL"),
                true,
                true,
                null,
                ImplementationContinuationConstraints.empty(),
                List.of(
                        new Subtask(
                                "建立运行入口",
                                "创建入口文件与最小表面",
                                List.of("CAP-1"),
                                List.of("页面可打开"),
                                List.of("补齐游戏逻辑"),
                                List.of("页面可打开"),
                                true,
                                DeliveryMode.SKELETON,
                                List.of(new FileChange(
                                        "index.html",
                                        ChangeAction.WRITE,
                                        "创建入口",
                                        FileEditScope.HOST_HTML_PATCH,
                                        RuntimeOwnershipMode.INLINE_HOST
                                ))
                        ),
                        new Subtask(
                                "补齐游戏逻辑",
                                "补齐核心行为",
                                List.of("CAP-2"),
                                List.of("输入可工作"),
                                List.of(),
                                List.of("输入可工作"),
                                false,
                                DeliveryMode.INCREMENTAL,
                                List.of(new FileChange("game.js", ChangeAction.WRITE, "补齐逻辑"))
                        )
                )
        ));

        assertTrue(report.passed(), report.issues().toString());
    }

    @Test
    void failsWhenHtmlEntryChangeOmitsRuntimeOwnership() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html"), List.of()),
                contractView(),
                List.of("index.html"),
                List.of("创建网页入口"),
                List.of("CAP-1"),
                List.of("SKELETON"),
                true,
                true,
                null,
                ImplementationContinuationConstraints.empty(),
                List.of(new Subtask(
                        "建立运行入口",
                        "创建入口文件与最小表面",
                        List.of("CAP-1"),
                        List.of("页面可打开"),
                        List.of(),
                        List.of("页面可打开"),
                        true,
                        DeliveryMode.SKELETON,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "创建入口"))
                ))
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.code().startsWith("PLAN_RUNTIME_")));
    }

    @Test
    void failsWhenExternalCompanionSubtaskDoesNotOwnCompanionFile() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html"), List.of()),
                contractView(),
                List.of("index.html"),
                List.of("补齐入口接线"),
                List.of("CAP-1"),
                List.of("PATCH"),
                true,
                true,
                null,
                ImplementationContinuationConstraints.empty(),
                List.of(new Subtask(
                        "补齐接线",
                        "把入口切到 companion runtime",
                        List.of("CAP-1"),
                        List.of("页面可启动"),
                        List.of(),
                        List.of("入口接线完成"),
                        true,
                        DeliveryMode.PATCH,
                        List.of(new FileChange(
                                "index.html",
                                ChangeAction.WRITE,
                                "改为 companion runtime",
                                FileEditScope.HOST_HTML_PATCH,
                                RuntimeOwnershipMode.EXTERNAL_COMPANION
                        ))
                ))
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("companion runtime")));
    }

    @Test
    void failsWhenContinuationPlanSwitchesProtectedRuntimeOwnership() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html"), List.of()),
                contractView(),
                List.of("index.html"),
                List.of("继续入口"),
                List.of("CAP-1"),
                List.of("PATCH"),
                true,
                true,
                null,
                new ImplementationContinuationConstraints(
                        List.of("index.html"),
                        List.of(new ImplementationContinuationConstraints.ProtectedHtmlEntryConstraint(
                                "index.html",
                                RuntimeOwnershipMode.INLINE_HOST
                        ))
                ),
                List.of(new Subtask(
                        "继续入口",
                        "补齐入口",
                        List.of("CAP-1"),
                        List.of("入口可运行"),
                        List.of(),
                        List.of("入口可运行"),
                        true,
                        DeliveryMode.PATCH,
                        List.of(
                                new FileChange(
                                        "index.html",
                                        ChangeAction.WRITE,
                                        "改成 companion",
                                        FileEditScope.HOST_HTML_PATCH,
                                        RuntimeOwnershipMode.EXTERNAL_COMPANION
                                ),
                                new FileChange("index.app.js", ChangeAction.WRITE, "补齐 companion runtime")
                        )
                ))
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("runtimeOwnership")));
    }

    private ContractView contractView() {
        return new ContractView(
                new ProductContract(
                        List.of("实现俄罗斯方块"),
                        List.of("浏览器用户"),
                        List.of("支持移动与旋转方块"),
                        List.of(),
                        List.of("用户可以通过方向键控制方块"),
                        List.of()
                ),
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "input-works")),
                null
        );
    }
}
