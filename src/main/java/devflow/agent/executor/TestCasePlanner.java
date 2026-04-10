package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.QualityPlanFactory;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.List;

/**
 * 测试用例规划门面。
 *
 * <p>它负责串联：
 * 1. 结构化 prompt 组装；
 * 2. 模型 testcase 规划调用；
 * 3. 确定性基础用例构造；
 * 4. 规划结果清洗与增强。
 *
 * <p>它不再直接承担 prompt 拼装、基础计划构造和结果强化细节，
 * 这些职责分别下沉到专用支撑类中。
 */
public class TestCasePlanner {

    private final LlmProvider llmProvider;
    private final ContractExtractor contractExtractor;
    private final StructuredPayloadReader structuredPayloadReader;
    private final TestCasePromptAssembler promptAssembler;
    private final TestCaseBasePlanBuilder basePlanBuilder;
    private final TestCasePlanSanitizer planSanitizer;
    private final CapabilityCoverageBackfillSupport coverageBackfillSupport;
    private final QualityPlanFactory qualityPlanFactory;

    public TestCasePlanner(FileProjectWorkspace workspace, LlmProvider llmProvider, ObjectMapper objectMapper) {
        this(workspace, llmProvider, objectMapper, new TreeSitterSupport(), new ContractExtractor());
    }

    TestCasePlanner(
            FileProjectWorkspace workspace,
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            TreeSitterSupport treeSitterSupport,
            ContractExtractor contractExtractor
    ) {
        this.llmProvider = llmProvider;
        this.contractExtractor = contractExtractor;
        this.structuredPayloadReader = new StructuredPayloadReader(objectMapper);
        this.promptAssembler = new TestCasePromptAssembler(workspace, contractExtractor);
        this.basePlanBuilder = new TestCaseBasePlanBuilder(workspace, treeSitterSupport);
        this.planSanitizer = new TestCasePlanSanitizer();
        this.coverageBackfillSupport = new CapabilityCoverageBackfillSupport();
        this.qualityPlanFactory = new QualityPlanFactory();
    }

    public TestCasePlan plan(
            Path projectPath,
            ProjectFingerprint fingerprint,
            String goal,
            String constraints,
            String prd,
            String design,
            String implementationReport,
            RuntimeSnapshot runtimeSnapshot
    ) {
        DocumentLanguage language = DocumentLanguage.detect(goal, constraints);
        String detectedEntry = resolveEntry(fingerprint);
        ContractView contractView = contractExtractor.extractContractView(goal, constraints, "", prd, design);
        ValidationMetadata validationMetadata = contractExtractor.extractValidationMetadata(prd, design);
        QualityPlan qualityPlan = qualityPlanFactory.build(
                projectPath,
                fingerprint,
                contractView,
                validationMetadata,
                runtimeSnapshot,
                java.util.List.of()
        );
        List<TestCaseSpec> baseCases = basePlanBuilder.build(
                projectPath,
                fingerprint,
                validationMetadata,
                qualityPlan,
                runtimeSnapshot,
                language
        );
        if (llmProvider == null) {
            return new TestCasePlan(
                    language.choose("未配置模型，使用确定性基础测试用例。", "No model configured; using deterministic base test cases."),
                    coverageBackfillSupport.backfill(baseCases, baseCases, qualityPlan),
                    qualityPlan
            );
        }

        try {
            TestCaseGenerationPrompt prompt = promptAssembler.assemble(
                    projectPath,
                    fingerprint,
                    goal,
                    constraints,
                    prd,
                    design,
                    implementationReport,
                    runtimeSnapshot,
                    qualityPlan
            );
            String response = llmProvider.generate(
                    prompt.systemPrompt(),
                    prompt.userPrompt(),
                    LlmOptions.outputBudgetRatio(TestPlanningPolicy.casePlanOutputRatio()),
                    ModelRole.TEST_CASE_DESIGN
            );
            PlannedTestCasesPayload payload = structuredPayloadReader.readJsonObject(response, PlannedTestCasesPayload.class);
            List<TestCaseSpec> planned = planSanitizer.sanitize(payload.cases(), baseCases, detectedEntry, runtimeSnapshot);
            planned = coverageBackfillSupport.backfill(planned, baseCases, qualityPlan);
            if (!planned.isEmpty()) {
                String summary = payload.summary() == null || payload.summary().isBlank()
                        ? (payload.cases() == null || payload.cases().isEmpty()
                        ? language.choose("使用确定性基础测试用例。", "Using deterministic base test cases.")
                        : language.choose("模型基于当前实现生成测试用例。", "The model generated test cases based on the current implementation."))
                        : payload.summary();
                return new TestCasePlan(summary, planned, qualityPlan);
            }
        } catch (Exception ignored) {
        }

        return new TestCasePlan(
                language.choose("使用确定性基础测试用例。", "Using deterministic base test cases."),
                coverageBackfillSupport.backfill(baseCases, baseCases, qualityPlan),
                qualityPlan
        );
    }

    private String resolveEntry(ProjectFingerprint fingerprint) {
        return fingerprint == null ? "" : fingerprint.resolvedHtmlEntryPath();
    }
}
