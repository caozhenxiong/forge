package devflow.agent.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.AuxiliaryArtifactNames;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.orchestrator.FileRunRepository;
import devflow.agent.orchestrator.RunConfig;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.RunStatus;
import devflow.agent.orchestrator.StageType;
import devflow.agent.protocol.ReviewHistoryEntryPayload;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisAgentTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldDiagnoseReadsStructuredReviewHistoryEntries() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        DiagnosisAgent diagnosisAgent = new DiagnosisAgent(similarityProvider(), artifactStore, new ObjectMapper());
        RunRecord runRecord = new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "实现数独",
                "纯前端",
                RunConfig.defaultConfig(),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        artifactStore.appendReviewHistory(
                tempDir,
                runRecord.runId(),
                StageType.IMPLEMENTATION,
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_HISTORY_ENTRY,
                        new ReviewHistoryEntryPayload(2, "agent", "IMPLEMENTATION", "REVISION_REQUIRED", "PATCH", ImplementationPatchTarget.NONE.name(), "实现没有收敛", "请修复同一处问题", "", "")
                )
        );
        artifactStore.appendReviewHistory(
                tempDir,
                runRecord.runId(),
                StageType.IMPLEMENTATION,
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_HISTORY_ENTRY,
                        new ReviewHistoryEntryPayload(3, "agent", "IMPLEMENTATION", "REVISION_REQUIRED", "PATCH", ImplementationPatchTarget.NONE.name(), "实现没有收敛", "请修复同一处问题", "", "")
                )
        );
        artifactStore.appendReviewHistory(
                tempDir,
                runRecord.runId(),
                StageType.IMPLEMENTATION,
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_HISTORY_ENTRY,
                        new ReviewHistoryEntryPayload(4, "agent", "IMPLEMENTATION", "REVISION_REQUIRED", "PATCH", ImplementationPatchTarget.NONE.name(), "实现没有收敛", "请修复同一处问题", "", "")
                )
        );

        assertTrue(diagnosisAgent.shouldDiagnose(
                tempDir,
                runRecord,
                StageType.IMPLEMENTATION,
                FixMode.PATCH,
                "实现没有收敛",
                "请修复同一处问题"
        ));
    }

    @Test
    void diagnoseIncludesReviewEvidenceAndLatestTestArtifacts() {
        FileRunRepository runRepository = new FileRunRepository();
        runRepository.initialize(tempDir);
        FileArtifactStore artifactStore = new FileArtifactStore(runRepository);
        AtomicReference<String> capturedPrompt = new AtomicReference<>("");
        DiagnosisAgent diagnosisAgent = new DiagnosisAgent(new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.DIAGNOSIS);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                capturedPrompt.set(userPrompt);
                return """
                        {
                          "failureCluster": "入口接线未收敛",
                          "repeatedErrors": ["入口仍未接线完成"],
                          "rootCauseHypothesis": "repair 没有覆盖关键接线项",
                          "affectedFiles": ["index.html", "game.js"],
                          "evidence": ["测试报告显示启动失败"],
                          "recommendedMode": "PATCH",
                          "mustFixFirst": ["补齐入口接线"],
                          "forbiddenDirections": ["不要继续只改样式"],
                          "doNotChange": ["不要重写整个项目"],
                          "acceptanceTarget": ["页面可启动"],
                          "acceptanceChecks": ["重新执行测试并确认入口正常"]
                        }
                        """;
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        }, artifactStore, new ObjectMapper());
        RunRecord runRecord = new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "实现数独",
                "纯前端",
                RunConfig.defaultConfig(),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        artifactStore.appendReviewHistory(
                tempDir,
                runRecord.runId(),
                StageType.IMPLEMENTATION,
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.REVIEW_HISTORY_ENTRY,
                        new ReviewHistoryEntryPayload(
                                2,
                                "agent",
                                "IMPLEMENTATION",
                                "REVISION_REQUIRED",
                                "PATCH",
                                ImplementationPatchTarget.NONE.name(),
                                "实现没有收敛",
                                "请修复入口接线",
                                "证据：启动按钮点击后没有进入 running 状态",
                                "动作：补齐 startGame 和事件绑定"
                        )
                )
        );
        artifactStore.writeAuxiliaryArtifact(
                tempDir,
                runRecord.runId(),
                AuxiliaryArtifactNames.TEST_RUNTIME_SNAPSHOT,
                "# 运行时快照\n\n- status: page-opened"
        );
        artifactStore.writeAuxiliaryArtifact(
                tempDir,
                runRecord.runId(),
                AuxiliaryArtifactNames.TEST_EXECUTION,
                "# 测试执行记录\n\n- case: 启动游戏\n- result: failed"
        );
        artifactStore.writeArtifact(
                tempDir,
                runRecord.runId(),
                StageType.TEST,
                "# 测试报告\n\n- summary: 启动后未进入 running"
        );

        diagnosisAgent.diagnose(
                tempDir,
                runRecord,
                StageType.IMPLEMENTATION,
                FixMode.PATCH,
                "实现没有收敛",
                "请修复入口接线"
        );

        assertTrue(capturedPrompt.get().contains("evidence: 证据：启动按钮点击后没有进入 running 状态"));
        assertTrue(capturedPrompt.get().contains("actionItems: 动作：补齐 startGame 和事件绑定"));
        assertTrue(capturedPrompt.get().contains("[test_runtime_snapshot.md]"));
        assertTrue(capturedPrompt.get().contains("page-opened"));
        assertTrue(capturedPrompt.get().contains("[test_execution.md]"));
        assertTrue(capturedPrompt.get().contains("启动游戏"));
        assertTrue(capturedPrompt.get().contains("[test_report.md]"));
        assertTrue(capturedPrompt.get().contains("running"));
    }

    private LlmProvider similarityProvider() {
        return new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return generate(systemPrompt, userPrompt, options, ModelRole.DIAGNOSIS);
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                if (systemPrompt.contains("FailureSimilarityJudge")) {
                    return """
                            {
                              "sameIssue": true,
                              "reason": "最近几轮失败仍属于同一类问题"
                            }
                            """;
                }
                return "{}";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
    }
}
