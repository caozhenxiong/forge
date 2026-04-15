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

import devflow.agent.context.ContractView;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ProductContract;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.validation.ProjectFingerprint;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devflow.agent.executor.subtask.Subtask;
class ImplementationPlanGateTests {

    @Test
    void succeedsWhenPlanUsesMinimalFileChangesWithoutRuntimeMetadata() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html"), List.of()),
                contractView(),
                PlanningRuntimeFacts.empty(),
                List.of("index.html", "src/app.js"),
                List.of("创建网页入口", "补齐脚本"),
                List.of("CAP-1", "CAP-2"),
                List.of("PATCH", "PATCH"),
                true,
                true,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
                List.of(),
                List.of(
                        new Subtask(
                                "建立入口",
                                "创建页面入口",
                                List.of("CAP-1"),
                                List.of("页面可打开"),
                                List.of("补齐脚本"),
                                List.of("页面可打开"),
                                true,
                                DeliveryMode.PATCH,
                                List.of(new FileChange("index.html", ChangeAction.WRITE, "创建入口"))
                        ),
                        new Subtask(
                                "补齐脚本",
                                "补齐运行脚本",
                                List.of("CAP-2"),
                                List.of("补齐脚本"),
                                List.of(),
                                List.of("输入可工作"),
                                false,
                                DeliveryMode.PATCH,
                                List.of(new FileChange("src/app.js", ChangeAction.WRITE, "补齐脚本"))
                        )
                )
        ));

        assertTrue(report.passed(), report.issues().toString());
    }

    @Test
    void failsWhenContinuationPlanRegressesExistingFileToSkeleton() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html"), List.of()),
                contractView(),
                PlanningRuntimeFacts.empty(),
                List.of("src/app.js"),
                List.of("继续修复脚本"),
                List.of("CAP-1"),
                List.of("SKELETON"),
                false,
                false,
                null,
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                new ImplementationContinuationConstraints(
                        List.of("src/app.js"),
                        List.of()
                ),
                List.of(),
                List.of(new Subtask(
                        "继续修复脚本",
                        "错误地把已有文件退回骨架",
                        List.of("CAP-1"),
                        List.of("脚本可运行"),
                        List.of(),
                        List.of("脚本可运行"),
                        false,
                        DeliveryMode.SKELETON,
                        List.of(new FileChange("src/app.js", ChangeAction.WRITE, "重做脚本"))
                ))
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("重新规划为 SKELETON")));
    }

    @Test
    void failsWhenContinuationPlanUsesReworkForProtectedHtmlEntry() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html"), List.of()),
                contractView(),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.inlineHost(java.nio.file.Path.of("index.html")),
                        List.of(),
                        List.of()
                ),
                List.of("index.html"),
                List.of("继续修复入口"),
                List.of("CAP-1"),
                List.of("REWORK"),
                true,
                false,
                null,
                ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                new ImplementationContinuationConstraints(
                        List.of("index.html"),
                        List.of(new ImplementationContinuationConstraints.ProtectedHtmlEntryConstraint("index.html"))
                ),
                List.of(),
                List.of(new Subtask(
                        "继续修复入口",
                        "错误地整页重写已有入口",
                        List.of("CAP-1"),
                        List.of("入口可打开"),
                        List.of(),
                        List.of("入口可打开"),
                        true,
                        DeliveryMode.REWORK,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "整页重写"))
                ))
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("REWORK/整页重写")));
    }

    @Test
    void failsWhenRuntimeSplitAddsCompanionScriptWithoutHostHtmlPatch() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html", "src/engine.js"), List.of()),
                contractView(),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.inlineHost(java.nio.file.Path.of("index.html")),
                        List.of(),
                        List.of()
                ),
                List.of("src/engine.js"),
                List.of("新增 companion runtime"),
                List.of("CAP-1"),
                List.of("PATCH"),
                false,
                false,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
                List.of(detail("拆 runtime",
                        new ImplementationSubtaskDetailChange("src/engine.js", ChangeAction.WRITE, "新增 runtime root", PlanningRuntimeScriptRole.ROOT))),
                List.of(new Subtask(
                        "拆 runtime",
                        "只新增 companion runtime",
                        List.of("CAP-1"),
                        List.of("runtime split"),
                        List.of(),
                        List.of("入口可运行"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("src/engine.js", ChangeAction.WRITE, "新增 runtime root"))
                ))
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("宿主 HTML patch")));
    }

    @Test
    void failsWhenCapabilityOwnedAndDeferredOverlap() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html"), List.of()),
                contractView(),
                PlanningRuntimeFacts.empty(),
                List.of("index.html"),
                List.of("建立入口"),
                List.of("CAP-1"),
                List.of("PATCH"),
                true,
                true,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
                List.of(),
                List.of(new Subtask(
                        "建立入口",
                        "创建页面入口",
                        List.of("CAP-1"),
                        List.of("页面可打开"),
                        List.of("页面可打开"),
                        List.of("页面可打开"),
                        true,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "创建入口"))
                ))
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("不能重叠")));
    }

    @Test
    void failsWhenCapabilityHasMultipleOwnedSubtasks() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html", "src/app.js"), List.of()),
                contractView(),
                PlanningRuntimeFacts.empty(),
                List.of("index.html", "src/app.js"),
                List.of("建立入口", "补齐脚本"),
                List.of("CAP-1", "CAP-2"),
                List.of("PATCH", "PATCH"),
                true,
                true,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
                List.of(),
                List.of(
                        new Subtask(
                                "建立入口",
                                "创建页面入口",
                                List.of("CAP-1"),
                                List.of("页面可打开"),
                                List.of(),
                                List.of("页面可打开"),
                                true,
                                DeliveryMode.PATCH,
                                List.of(new FileChange("index.html", ChangeAction.WRITE, "创建入口"))
                        ),
                        new Subtask(
                                "补齐脚本",
                                "补齐运行脚本",
                                List.of("CAP-2"),
                                List.of("页面可打开"),
                                List.of(),
                                List.of("输入可工作"),
                                false,
                                DeliveryMode.PATCH,
                                List.of(new FileChange("src/app.js", ChangeAction.WRITE, "补齐脚本"))
                        )
                )
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("多个子任务同时声明为 ownedCapabilities")));
    }

    @Test
    void failsWhenDeferredCapabilityDoesNotHaveUniqueFutureOwner() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html", "src/app.js"), List.of()),
                contractView(),
                PlanningRuntimeFacts.empty(),
                List.of("index.html", "src/app.js"),
                List.of("建立入口", "补齐脚本"),
                List.of("CAP-1", "CAP-2"),
                List.of("PATCH", "PATCH"),
                true,
                true,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
                List.of(),
                List.of(
                        new Subtask(
                                "建立入口",
                                "创建页面入口",
                                List.of("CAP-1"),
                                List.of("页面可打开"),
                                List.of("输入可工作"),
                                List.of("页面可打开"),
                                true,
                                DeliveryMode.PATCH,
                                List.of(new FileChange("index.html", ChangeAction.WRITE, "创建入口"))
                        ),
                        new Subtask(
                                "补齐脚本",
                                "补齐运行脚本",
                                List.of("CAP-2"),
                                List.of(),
                                List.of(),
                                List.of("输入可工作"),
                                false,
                                DeliveryMode.PATCH,
                                List.of(new FileChange("src/app.js", ChangeAction.WRITE, "补齐脚本"))
                        )
                )
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("必须由后续唯一子任务接手")));
    }

    @Test
    void failsWhenSharedFileIncrementalSubtaskOmitsDeferredCapabilities() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html", "src/game.js"), List.of()),
                contractView(),
                PlanningRuntimeFacts.empty(),
                List.of("index.html", "src/game.js", "src/game.js"),
                List.of("建立入口和画布", "继续补 gameplay"),
                List.of("CAP-1", "CAP-2"),
                List.of("PATCH", "PATCH"),
                true,
                true,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
                List.of(),
                List.of(
                        new Subtask(
                                "建立入口和画布",
                                "创建页面入口和基础画布",
                                List.of("CAP-1"),
                                List.of("画布壳层"),
                                List.of(),
                                List.of("页面可打开"),
                                true,
                                DeliveryMode.PATCH,
                                List.of(
                                        new FileChange("index.html", ChangeAction.WRITE, "创建入口"),
                                        new FileChange("src/game.js", ChangeAction.WRITE, "创建基础画布")
                                )
                        ),
                        new Subtask(
                                "继续补 gameplay",
                                "在同一文件里补充后续玩法能力",
                                List.of("CAP-2"),
                                List.of("gameplay"),
                                List.of(),
                                List.of("玩法可工作"),
                                false,
                                DeliveryMode.PATCH,
                                List.of(new FileChange("src/game.js", ChangeAction.WRITE, "补充玩法逻辑"))
                        )
                )
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("deferredCapabilities")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("src/game.js")));
    }

    @Test
    void allowsSharedFileIncrementalSubtaskWhenDeferredCapabilitiesAreExplicit() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html", "src/game.js"), List.of()),
                contractView(),
                PlanningRuntimeFacts.empty(),
                List.of("index.html", "src/game.js", "src/game.js"),
                List.of("建立入口和画布", "继续补 gameplay"),
                List.of("CAP-1", "CAP-2"),
                List.of("PATCH", "PATCH"),
                true,
                true,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
                List.of(),
                List.of(
                        new Subtask(
                                "建立入口和画布",
                                "创建页面入口和基础画布",
                                List.of("CAP-1"),
                                List.of("画布壳层"),
                                List.of("gameplay"),
                                List.of("页面可打开"),
                                true,
                                DeliveryMode.PATCH,
                                List.of(
                                        new FileChange("index.html", ChangeAction.WRITE, "创建入口"),
                                        new FileChange("src/game.js", ChangeAction.WRITE, "创建基础画布")
                                )
                        ),
                        new Subtask(
                                "继续补 gameplay",
                                "在同一文件里补充后续玩法能力",
                                List.of("CAP-2"),
                                List.of("gameplay"),
                                List.of(),
                                List.of("玩法可工作"),
                                false,
                                DeliveryMode.PATCH,
                                List.of(new FileChange("src/game.js", ChangeAction.WRITE, "补充玩法逻辑"))
                        )
                )
        ));

        assertTrue(report.passed(), report.issues().toString());
    }

    @Test
    void failsWhenSharedFileDeferredCapabilitiesDoNotAnchorToFutureOwner() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html", "src/game.js"), List.of()),
                contractView(),
                PlanningRuntimeFacts.empty(),
                List.of("index.html", "src/game.js", "src/game.js", "src/admin.js"),
                List.of("建立入口和画布", "继续补 gameplay", "补管理功能"),
                List.of("CAP-1", "CAP-2", "CAP-3"),
                List.of("PATCH", "PATCH", "PATCH"),
                true,
                true,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
                List.of(),
                List.of(
                        new Subtask(
                                "建立入口和画布",
                                "创建页面入口和基础画布",
                                List.of("CAP-1"),
                                List.of("画布壳层"),
                                List.of("admin"),
                                List.of("页面可打开"),
                                true,
                                DeliveryMode.PATCH,
                                List.of(
                                        new FileChange("index.html", ChangeAction.WRITE, "创建入口"),
                                        new FileChange("src/game.js", ChangeAction.WRITE, "创建基础画布")
                                )
                        ),
                        new Subtask(
                                "继续补 gameplay",
                                "在同一文件里补充后续玩法能力",
                                List.of("CAP-2"),
                                List.of("gameplay"),
                                List.of(),
                                List.of("玩法可工作"),
                                false,
                                DeliveryMode.PATCH,
                                List.of(new FileChange("src/game.js", ChangeAction.WRITE, "补充玩法逻辑"))
                        ),
                        new Subtask(
                                "补管理功能",
                                "补充不共享文件的能力",
                                List.of("CAP-3"),
                                List.of("admin"),
                                List.of(),
                                List.of("管理功能可工作"),
                                false,
                                DeliveryMode.PATCH,
                                List.of(new FileChange("src/admin.js", ChangeAction.WRITE, "补充管理逻辑"))
                        )
                )
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("下游能力")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("gameplay")));
    }

    @Test
    void allowsRuntimeLeafWhenScopeAlsoPatchesCurrentlyReachableRuntimeModule() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html", "index.app.js", "src/engine.js"), List.of()),
                contractView(),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.externalCompanion(
                                java.nio.file.Path.of("index.html"),
                                List.of(java.nio.file.Path.of("index.app.js"))
                        ),
                        List.of(java.nio.file.Path.of("index.app.js")),
                        List.of(java.nio.file.Path.of("index.app.js"))
                ),
                List.of("index.html", "index.app.js", "src/engine.js"),
                List.of("扩展已有 runtime"),
                List.of("CAP-1"),
                List.of("PATCH"),
                true,
                true,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
                List.of(detail("扩展已有 runtime",
                        new ImplementationSubtaskDetailChange("index.app.js", ChangeAction.WRITE, "扩展已有 root"),
                        new ImplementationSubtaskDetailChange("src/engine.js", ChangeAction.WRITE, "新增 leaf module", PlanningRuntimeScriptRole.LEAF))),
                List.of(new Subtask(
                        "扩展已有 runtime",
                        "在当前 reachable runtime 模块下新增 leaf module",
                        List.of("CAP-1"),
                        List.of("runtime wiring"),
                        List.of(),
                        List.of("入口可运行"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(
                                new FileChange("index.app.js", ChangeAction.WRITE, "扩展已有 root"),
                                new FileChange("src/engine.js", ChangeAction.WRITE, "新增 leaf module")
                        )
                ))
        ));

        assertTrue(report.passed(), report.issues().toString());
    }

    @Test
    void failsWhenNewRuntimeScriptOmitsRoleEvenWithReachableAnchor() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html", "index.app.js", "src/engine.js"), List.of()),
                contractView(),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.externalCompanion(
                                java.nio.file.Path.of("index.html"),
                                List.of(java.nio.file.Path.of("index.app.js"))
                        ),
                        List.of(java.nio.file.Path.of("index.app.js")),
                        List.of(java.nio.file.Path.of("index.app.js"))
                ),
                List.of("index.html", "index.app.js", "src/engine.js"),
                List.of("扩展已有 runtime"),
                List.of("CAP-1"),
                List.of("PATCH"),
                true,
                true,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
                List.of(detail("扩展已有 runtime",
                        new ImplementationSubtaskDetailChange("index.app.js", ChangeAction.WRITE, "扩展已有 root"),
                        new ImplementationSubtaskDetailChange("src/engine.js", ChangeAction.WRITE, "新增 leaf module"))),
                List.of(new Subtask(
                        "扩展已有 runtime",
                        "在当前 reachable runtime 模块下新增 leaf module",
                        List.of("CAP-1"),
                        List.of("runtime wiring"),
                        List.of(),
                        List.of("入口可运行"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(
                                new FileChange("index.app.js", ChangeAction.WRITE, "扩展已有 root"),
                                new FileChange("src/engine.js", ChangeAction.WRITE, "新增 leaf module")
                        )
                ))
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("runtimeScriptRole=ROOT|LEAF")));
    }

    @Test
    void failsWhenKnownOrphanRuntimeRootIsAddedWithoutHostPatch() {
        ImplementationPlanGate gate = new ImplementationPlanGate(new ImplementationPlanCoverageAnalyzer());
        GateReport report = gate.evaluate(new ImplementationPlanGateInput(
                new ProjectFingerprint("web", "none", false, false, false, false, true, true, false, "index.html", Set.of("index.html", "index.app.js", "admin.app.js"), List.of()),
                contractView(),
                new PlanningRuntimeFacts(
                        java.nio.file.Path.of("index.html"),
                        HtmlRuntimeOwnershipContract.externalCompanion(
                                java.nio.file.Path.of("index.html"),
                                List.of(java.nio.file.Path.of("index.app.js"))
                        ),
                        List.of(java.nio.file.Path.of("index.app.js")),
                        List.of(java.nio.file.Path.of("index.app.js"), java.nio.file.Path.of("admin.app.js"))
                ),
                List.of("index.app.js", "admin.app.js"),
                List.of("混合 runtime root"),
                List.of("CAP-1"),
                List.of("PATCH"),
                true,
                true,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
                List.of(detail("混合 runtime root",
                        new ImplementationSubtaskDetailChange("index.app.js", ChangeAction.WRITE, "扩展已有 root"),
                        new ImplementationSubtaskDetailChange("admin.app.js", ChangeAction.WRITE, "引入另一条 root", PlanningRuntimeScriptRole.ROOT))),
                List.of(new Subtask(
                        "混合 runtime root",
                        "在当前 wired root 旁引入另一个已知 orphan root",
                        List.of("CAP-1"),
                        List.of("runtime wiring"),
                        List.of(),
                        List.of("入口可运行"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(
                                new FileChange("index.app.js", ChangeAction.WRITE, "扩展已有 root"),
                                new FileChange("admin.app.js", ChangeAction.WRITE, "引入另一条 root")
                        )
                ))
        ));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("宿主 HTML patch")));
    }

    private ContractView contractView() {
        return new ContractView(
                ProductContract.projectedFromPrdSections(
                        List.of("实现俄罗斯方块"),
                        List.of("浏览器用户"),
                        List.of("支持移动与旋转方块"),
                        List.of(),
                        List.of(),
                        List.of("用户可以通过方向键控制方块"),
                        List.of()
                ),
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "input-works")),
                null
        );
    }

    private ImplementationSubtaskDetail detail(
            String subtaskId,
            ImplementationSubtaskDetailChange... changes
    ) {
        return new ImplementationSubtaskDetail(subtaskId, List.of(changes));
    }
}
