package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.context.ExecutionContract;
import devflow.agent.quality.CapabilitySurface;
import devflow.agent.quality.QualityCoverageRefCatalog;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.QualityPlanFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationPlanCoverageAnalyzerTests {

    @Test
    void executionContractRequiringLaunchMustHaveRunnableMilestoneOwner() {
        ImplementationPlanCoverageAnalyzer analyzer = new ImplementationPlanCoverageAnalyzer();
        ContractView contractView = new ContractView(
                null,
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "surface-renders")),
                null
        );

        CoverageResult result = analyzer.analyze(
                null,
                contractView,
                List.of("index.html", "game-engine.js"),
                List.of("创建网页入口", "补齐核心逻辑"),
                List.of("CAP-1"),
                List.of("SKELETON", "INCREMENTAL"),
                false,
                false,
                null
        );

        assertFalse(result.passed());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("可运行里程碑")));
    }

    @Test
    void planMustCoverProductCapabilitiesUsingStructuredCoverageRefs() {
        ImplementationPlanCoverageAnalyzer analyzer = new ImplementationPlanCoverageAnalyzer();
        ContractView contractView = new ContractView(
                new devflow.agent.context.ProductContract(
                        List.of("实现俄罗斯方块"),
                        List.of("浏览器用户"),
                        List.of("支持移动与旋转方块", "提供计分显示", "提供下一个方块预览"),
                        List.of(),
                        List.of("用户可以通过方向键控制方块"),
                        List.of(),
                        List.of(
                                new devflow.agent.context.RequirementReference("CAP-1", "required-capability", "支持移动与旋转方块", true),
                                new devflow.agent.context.RequirementReference("CAP-2", "required-capability", "提供计分显示", true),
                                new devflow.agent.context.RequirementReference("CAP-3", "required-capability", "提供下一个方块预览", true)
                        )
                ),
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "input-works")),
                null
        );

        CoverageResult result = analyzer.analyze(
                null,
                contractView,
                List.of("index.html", "style.css"),
                List.of("创建网页入口", "建立游戏表面", "开始按钮可见"),
                List.of("CAP-1"),
                List.of("SKELETON", "INCREMENTAL"),
                true,
                true,
                null
        );

        assertFalse(result.passed());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("计分") || issue.contains("预览") || issue.contains("移动")));
    }

    @Test
    void planPassesWhenStructuredCoverageRefsCoverRequiredCapabilitiesAndAcceptance() {
        ImplementationPlanCoverageAnalyzer analyzer = new ImplementationPlanCoverageAnalyzer();
        ContractView contractView = new ContractView(
                new devflow.agent.context.ProductContract(
                        List.of("实现俄罗斯方块"),
                        List.of("浏览器用户"),
                        List.of("支持移动与旋转方块", "提供计分显示"),
                        List.of(),
                        List.of("用户可以通过方向键控制方块"),
                        List.of(),
                        List.of(
                                new devflow.agent.context.RequirementReference("CAP-1", "required-capability", "支持移动与旋转方块", true),
                                new devflow.agent.context.RequirementReference("CAP-2", "required-capability", "提供计分显示", true)
                        )
                ),
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "input-works")),
                null
        );

        CoverageResult result = analyzer.analyze(
                null,
                contractView,
                List.of("index.html", "main.js"),
                List.of("创建网页入口", "实现游戏逻辑"),
                List.of("CAP-1", "CAP-2"),
                List.of("SKELETON", "INCREMENTAL"),
                true,
                true,
                null
        );

        assertTrue(result.passed(), result.issues().toString());
    }

    @Test
    void acceptanceCriteriaRefsAreNotMandatoryForPlanCoverage() {
        ImplementationPlanCoverageAnalyzer analyzer = new ImplementationPlanCoverageAnalyzer();
        ContractView contractView = new ContractView(
                new devflow.agent.context.ProductContract(
                        List.of("实现俄罗斯方块"),
                        List.of("浏览器用户"),
                        List.of("支持移动与旋转方块", "提供计分显示"),
                        List.of("代码结构清晰"),
                        List.of("支持单元测试和集成测试", "用户可以通过方向键控制方块"),
                        List.of(),
                        List.of(
                                new devflow.agent.context.RequirementReference("CAP-1", "required-capability", "支持移动与旋转方块", true),
                                new devflow.agent.context.RequirementReference("CAP-2", "required-capability", "提供计分显示", true),
                                new devflow.agent.context.RequirementReference("ACC-1", "acceptance-criterion", "支持单元测试和集成测试", false),
                                new devflow.agent.context.RequirementReference("ACC-2", "acceptance-criterion", "用户可以通过方向键控制方块", false)
                        )
                ),
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "input-works")),
                null
        );

        CoverageResult result = analyzer.analyze(
                null,
                contractView,
                List.of("index.html", "main.js"),
                List.of("创建网页入口", "实现游戏逻辑与输入"),
                List.of("CAP-1", "CAP-2"),
                List.of("SKELETON", "INCREMENTAL"),
                true,
                true,
                null
        );

        assertTrue(result.passed(), result.issues().toString());
    }

    @Test
    void proseCapabilitiesDoNotBecomePlanningRequirementsWithoutStructuredRefs() {
        ImplementationPlanCoverageAnalyzer analyzer = new ImplementationPlanCoverageAnalyzer();
        ContractView contractView = new ContractView(
                new devflow.agent.context.ProductContract(
                        List.of("实现俄罗斯方块"),
                        List.of("浏览器用户"),
                        List.of("支持移动与旋转方块", "建议：提供状态提示"),
                        List.of(),
                        List.of("用户可以通过方向键控制方块"),
                        List.of()
                ),
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "input-works")),
                null
        );

        CoverageResult result = analyzer.analyze(
                null,
                contractView,
                List.of("index.html", "main.js"),
                List.of("创建网页入口", "实现游戏逻辑"),
                List.of("CAP-1"),
                List.of("SKELETON", "INCREMENTAL"),
                true,
                true,
                null
        );

        assertTrue(result.passed(), result.issues().toString());
    }

    @Test
    void proseCapabilitiesWithOptionalWordsDoNotAffectPlanningCoverage() {
        ImplementationPlanCoverageAnalyzer analyzer = new ImplementationPlanCoverageAnalyzer();
        ContractView contractView = new ContractView(
                new devflow.agent.context.ProductContract(
                        List.of("实现俄罗斯方块"),
                        List.of("浏览器用户"),
                        List.of("支持移动与旋转方块", "提供空格键快速下落（可选）", "提供基本操作提示（optional）"),
                        List.of(),
                        List.of("用户可以通过方向键控制方块"),
                        List.of()
                ),
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "input-works")),
                null
        );

        CoverageResult result = analyzer.analyze(
                null,
                contractView,
                List.of("index.html", "main.js"),
                List.of("创建网页入口", "实现游戏逻辑"),
                List.of("CAP-1"),
                List.of("SKELETON", "INCREMENTAL"),
                true,
                true,
                null
        );

        assertTrue(result.passed(), result.issues().toString());
    }

    @Test
    void requiredQualityCapabilityRefsMustBeAssignedInImplementationPlan() {
        ImplementationPlanCoverageAnalyzer analyzer = new ImplementationPlanCoverageAnalyzer();
        ContractView contractView = new ContractView(
                null,
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "surface-renders")),
                null
        );
        QualityPlan qualityPlan = new QualityPlanFactory().build(
                null,
                contractView,
                null,
                null,
                List.of(
                        CapabilitySurface.PRIMARY_INTERACTION.wireValue(),
                        CapabilitySurface.TIMED_STATE_PROGRESSION.wireValue()
                )
        );

        CoverageResult result = analyzer.analyze(
                null,
                contractView,
                List.of("index.html", "index.app.js"),
                List.of("创建入口", "补齐逻辑"),
                QualityCoverageRefCatalog.requiredReferenceIds(qualityPlan).stream()
                        .filter(ref -> !ref.equals(QualityCoverageRefCatalog.referenceId(CapabilitySurface.TIMED_STATE_PROGRESSION)))
                        .toList(),
                List.of("SKELETON", "INCREMENTAL"),
                true,
                true,
                qualityPlan
        );

        assertFalse(result.passed());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("timed-state-progression")));
    }

    @Test
    void requiredQualityCapabilityRefsPassWhenAllSurfacesAreAssigned() {
        ImplementationPlanCoverageAnalyzer analyzer = new ImplementationPlanCoverageAnalyzer();
        ContractView contractView = new ContractView(
                null,
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "surface-renders")),
                null
        );
        QualityPlan qualityPlan = new QualityPlanFactory().build(
                null,
                contractView,
                null,
                null,
                List.of(
                        CapabilitySurface.PRIMARY_INTERACTION.wireValue(),
                        CapabilitySurface.TIMED_STATE_PROGRESSION.wireValue()
                )
        );

        CoverageResult result = analyzer.analyze(
                null,
                contractView,
                List.of("index.html", "index.app.js"),
                List.of("创建入口", "补齐逻辑"),
                QualityCoverageRefCatalog.requiredReferenceIds(qualityPlan).stream().toList(),
                List.of("SKELETON", "INCREMENTAL"),
                true,
                true,
                qualityPlan
        );

        assertTrue(result.passed(), result.issues().toString());
    }

    @Test
    void htmlRunnableSkeletonMayBundleStandaloneRuntimeScriptModulesWhenItStillOwnsEntry() {
        ImplementationPlanCoverageAnalyzer analyzer = new ImplementationPlanCoverageAnalyzer();
        ContractView contractView = new ContractView(
                null,
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "surface-renders")),
                null
        );

        CoverageResult result = analyzer.analyzeRunnableMilestones(
                contractView,
                List.of(new Subtask(
                        "建立运行表面",
                        "创建最小可运行入口",
                        List.of(),
                        List.of("页面可打开"),
                        List.of("补齐游戏逻辑"),
                        List.of("页面可打开"),
                        true,
                        DeliveryMode.SKELETON,
                        List.of(
                                new FileChange("index.html", ChangeAction.WRITE, "创建入口"),
                                new FileChange("game.js", ChangeAction.WRITE, "拆出逻辑模块")
                        )
                ))
        );

        assertTrue(result.passed(), result.issues().toString());
    }

    @Test
    void htmlEntryPlanMayContinueEditingSameEntryFileAcrossMultipleSubtasks() {
        ImplementationPlanCoverageAnalyzer analyzer = new ImplementationPlanCoverageAnalyzer();
        ContractView contractView = new ContractView(
                null,
                null,
                new ExecutionContract(true, "html-entry", true, true, List.of("page-opens", "surface-renders")),
                null
        );

        CoverageResult result = analyzer.analyzeRunnableMilestones(
                contractView,
                List.of(
                        new Subtask(
                                "建立入口",
                                "建立入口",
                                List.of("CAP-1"),
                                List.of("页面可打开"),
                                List.of("后续补逻辑"),
                                List.of("页面可打开"),
                                true,
                                DeliveryMode.SKELETON,
                                List.of(new FileChange("index.html", ChangeAction.WRITE, "建立入口"))
                        ),
                        new Subtask(
                                "补齐核心逻辑",
                                "补齐逻辑",
                                List.of("CAP-2"),
                                List.of("核心逻辑可运行"),
                                List.of("后续补计分"),
                                List.of("逻辑可运行"),
                                false,
                                DeliveryMode.INCREMENTAL,
                                List.of(new FileChange("index.html", ChangeAction.WRITE, "继续在入口文件内做稳定增量编辑"))
                        ),
                        new Subtask(
                                "补齐计分与预览",
                                "补齐计分",
                                List.of("CAP-3"),
                                List.of("计分可用"),
                                List.of(),
                                List.of("计分可用"),
                                false,
                                DeliveryMode.INCREMENTAL,
                                List.of(new FileChange("index.html", ChangeAction.WRITE, "继续在入口文件内补齐后续能力"))
                        )
                )
        );

        assertTrue(result.passed(), result.issues().toString());
    }
}
