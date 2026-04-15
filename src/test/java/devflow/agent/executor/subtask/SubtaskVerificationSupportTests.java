package devflow.agent.executor.subtask;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.domain.RunRecord;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.gate.ImplementationCompletenessCheck;
import devflow.agent.executor.gate.ImplementationCompletenessGate;
import devflow.agent.executor.gate.ImplementationCompletenessGateOutcome;
import devflow.agent.executor.gate.ImplementationCompletenessResult;
import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.testing.TestExecutor;
import devflow.agent.executor.testing.TestExecutorTestSupport;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.loop.AgentTurnLoop;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.QualityPlan;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewRevisionRoute;
import devflow.agent.review.ReviewSemantics;
import devflow.agent.review.StructuredReviewResult;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtaskVerificationSupportTests {

    @TempDir
    Path tempDir;

    @Test
    void reviewProducedRuntimeWiringPatchWithoutCanonicalPackageRequestsHuman() throws Exception {
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("app.js"), "export const ready = true;\n");
        FileProjectWorkspace workspace = new FileProjectWorkspace();
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }

            @Override
            public StructuredReviewResult reviewStructured(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new StructuredReviewResult(
                        new ReviewResult(
                                ReviewDecision.REVISION_REQUIRED,
                                FixMode.PATCH,
                                "需要修复 runtime wiring",
                                "请补齐 runtime wiring",
                                "review missing canonical runtime package",
                                "",
                                ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                                List.of(),
                                ReviewRevisionRoute.PATCH_CURRENT_STAGE,
                                ReviewReasonCode.RUNTIME_WIRING_GAP
                        ),
                        ReviewSemantics.empty()
                );
            }
        };
        TestExecutor testExecutor = TestExecutorTestSupport.create(workspace, provider, new ObjectMapper());
        SubtaskVerificationSupport support = new SubtaskVerificationSupport(
                testExecutor,
                provider,
                new GenerationEngine(),
                new ImplementationCompletenessGate(new ImplementationCompletenessCheck(workspace, new TreeSitterSupport())),
                new devflow.agent.executor.gate.ArchitectIntegrationCheck(workspace, new TreeSitterSupport()),
                workspace,
                new AgentTurnLoop(),
                new SubtaskReviewPolicy(),
                new SubtaskPerformanceGuidanceResolver(new devflow.agent.context.ContractExtractor()),
                new TreeSitterSupport()
        );

        SubtaskVerificationOutcome outcome = support.verifySubtask(
                projectDir,
                new RunRecord(
                        UUID.randomUUID(),
                        projectDir,
                        "goal",
                        "constraints",
                        null,
                        null,
                        null,
                        Map.of(),
                        Instant.now(),
                        Instant.now()
                ),
                new Subtask(
                        "修脚本",
                        "修 app.js",
                        List.of(),
                        List.of("脚本可运行"),
                        List.of(),
                        List.of("脚本可运行"),
                        false,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("app.js", ChangeAction.WRITE, "修复脚本"))
                ),
                new SelfCheckResult(true, "ok", ""),
                List.of(),
                "",
                ImplementationCompletenessResult.success(),
                new ImplementationCompletenessGateOutcome(ImplementationCompletenessResult.success(), devflow.agent.executor.gate.GateReport.success()),
                false,
                null,
                QualityPlan.empty(),
                new ProjectFingerprint("none", "none", false, false, false, false, false, true, false, "", Set.of("app.js"), List.of()),
                DocumentLanguage.ZH,
                "",
                new ImplementationEventJournal(null, null, projectDir, null)
        );

        assertNotNull(outcome);
        assertEquals(ReviewRevisionRoute.REQUEST_HUMAN, outcome.review().revisionRoute());
        assertEquals(ImplementationPatchTarget.NONE, outcome.review().implementationPatchTarget());
        assertTrue(outcome.review().changeRequest().contains("canonical runtime repair package"));
        assertTrue(outcome.revisionDirective().retryChanges().isEmpty());
    }
}
