package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ReviewArtifactPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.protocol.ToolResultPayload;
import devflow.agent.protocol.ToolResultsArtifactPayload;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import java.util.List;

/**
 * 统一维护测试阶段的人类可读产物渲染。
 *
 * <p>TestExecutor 负责测试编排与证据收集，这个类负责把
 * testcase、执行记录和最终报告稳定地渲染成 markdown，
 * 避免测试执行层再次混入大量文案拼接逻辑。
 */
class TestArtifactRenderer {

    private final TestCaseArtifactRenderer testCaseArtifactRenderer = new TestCaseArtifactRenderer();
    private final TestExecutionArtifactRenderer testExecutionArtifactRenderer = new TestExecutionArtifactRenderer();
    private final TestReportArtifactRenderer testReportArtifactRenderer = new TestReportArtifactRenderer();

    String renderTestCases(TestCasePlan plan, DocumentLanguage language) {
        return testCaseArtifactRenderer.render(plan, language);
    }

    String renderExecution(CollectedTestEvidence evidence, DocumentLanguage language) {
        return testExecutionArtifactRenderer.render(evidence, language);
    }

    String renderReport(
            String note,
            CollectedTestEvidence evidence,
            TestEvidenceGateOutcome evidenceOutcome,
            DocumentLanguage language
    ) {
        return testReportArtifactRenderer.render(note, evidence, evidenceOutcome, language);
    }
}
