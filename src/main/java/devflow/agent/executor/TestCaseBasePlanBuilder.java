package devflow.agent.executor;

import devflow.agent.context.ValidationMetadata;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.quality.QualityPlan;
import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 确定性基础测试用例构造器。
 *
 * <p>它根据入口文件、运行时快照、HTML 结构和验证元数据生成一组
 * 保守且可执行的基础测试用例，供模型 refinement 和 coverage 回填使用。
 */
final class TestCaseBasePlanBuilder {

    private final FileProjectWorkspace workspace;
    private final TreeSitterSupport treeSitterSupport;
    private final HtmlStructureCaseBuilder htmlStructureCaseBuilder;
    private final PerformanceCaseBuilder performanceCaseBuilder;

    TestCaseBasePlanBuilder(FileProjectWorkspace workspace, TreeSitterSupport treeSitterSupport) {
        this.workspace = workspace;
        this.treeSitterSupport = treeSitterSupport;
        this.htmlStructureCaseBuilder = new HtmlStructureCaseBuilder();
        this.performanceCaseBuilder = new PerformanceCaseBuilder();
    }

    List<TestCaseSpec> build(
            Path projectPath,
            ProjectFingerprint fingerprint,
            ValidationMetadata validationMetadata,
            QualityPlan qualityPlan,
            RuntimeSnapshot runtimeSnapshot,
            UiRuntimeContract runtimeContract,
            DocumentLanguage language
    ) {
        List<TestCaseSpec> cases = new ArrayList<>();
        String entry = resolveEntry(fingerprint);
        if (fingerprint.hasResolvedHtmlEntry()) {
            cases.add(new TestCaseSpec(
                    "TC-SMOKE-LOAD",
                    language.choose("页面可加载且无致命错误", "Page loads without fatal errors"),
                    "smoke",
                    true,
                    entry,
                    "",
                    language.choose("页面能正常打开，且没有运行时错误。", "The page opens successfully and has no runtime errors."),
                    List.of(
                            new TestStepSpec(TestStepAction.ASSERT_SELECTOR, "body", null, null, null, null, false),
                            new TestStepSpec(TestStepAction.ASSERT_NO_ERRORS, null, null, null, null, null, false)
                    )
            ));
        }

        try {
            if (fingerprint.hasResolvedHtmlEntry()) {
                String html = workspace.readFile(projectPath, Path.of(entry));
                HtmlStructureSnapshot htmlSnapshot = treeSitterSupport.inspectHtml(html);
                htmlStructureCaseBuilder.appendPrimarySurfaceCases(cases, entry, runtimeContract, language);
                htmlStructureCaseBuilder.appendButtonCases(cases, entry, htmlSnapshot, runtimeSnapshot, runtimeContract, language);
                performanceCaseBuilder.appendPerformanceCases(cases, entry, runtimeSnapshot, validationMetadata, language);
            }
        } catch (Exception ignored) {
        }

        if (cases.isEmpty()) {
            cases.add(new TestCaseSpec(
                    "TC-BASIC",
                    language.choose("基础自检用例", "Basic self-check case"),
                    "smoke",
                    true,
                    entry,
                    "",
                    language.choose("至少执行一轮基础检查。", "Run at least one basic verification step."),
                    List.of(new TestStepSpec(TestStepAction.ASSERT_NO_ERRORS, null, null, null, null, null, false))
            ));
        }
        return List.copyOf(cases);
    }

    private String resolveEntry(ProjectFingerprint fingerprint) {
        return fingerprint == null ? "" : fingerprint.resolvedHtmlEntryPath();
    }
}
