package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

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
                List.of("index.html", "src/app.js"),
                List.of("创建网页入口", "补齐脚本"),
                List.of("CAP-1", "CAP-2"),
                List.of("PATCH", "PATCH"),
                true,
                true,
                null,
                ImplementationPatchTarget.NONE,
                ImplementationContinuationConstraints.empty(),
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
                                List.of("输入可工作"),
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
}
