package devflow.agent.orchestrator;

import devflow.agent.executor.generation.GenerationEngine;
import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.llm.ModelRole;

import devflow.agent.domain.RunConfig;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.RunStatus;
import devflow.agent.domain.StageExecution;
import devflow.agent.domain.StageStatus;
import devflow.agent.domain.StageType;

import devflow.agent.artifact.EventLogStore;
import devflow.agent.executor.testing.TestExecutor;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.project.WorkspaceSnapshotStore;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ImplementationStageStatusPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.StageReviewer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageOperationExecutorTests {

    @TempDir
    Path tempDir;

    @Test
    void implementationReviewDelegatesToReviewer() {
        AtomicBoolean reviewerCalled = new AtomicBoolean(false);
        StageOperationExecutor executor = new StageOperationExecutor(
                null,
                reviewerThatReturns(reviewerCalled, new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "")),
                new EventLogStore(new FileRunRepository()),
                new GenerationEngine(),
                new StageOperationPolicy()
        );

        ReviewResult result = executor.reviewStage(
                tempDir,
                runRecord(),
                StageType.IMPLEMENTATION,
                new StageExecution(StageType.IMPLEMENTATION, StageStatus.RUNNING, 1, null, null, null, null),
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                        new ImplementationStageStatusPayload(false, false, java.util.List.of("补齐核心逻辑"))
                ),
                DocumentLanguage.ZH
        );

        assertTrue(reviewerCalled.get());
        assertEquals(ReviewDecision.APPROVED, result.decision());
        assertEquals(FixMode.NONE, result.fixMode());
    }

    @Test
    void implementationReviewReturnsReviewerDecision() {
        AtomicBoolean reviewerCalled = new AtomicBoolean(false);
        ReviewResult expected = new ReviewResult(ReviewDecision.REVISION_REQUIRED, FixMode.PATCH, "needs patch", "fix it");
        StageOperationExecutor executor = new StageOperationExecutor(
                null,
                reviewerThatReturns(reviewerCalled, expected),
                new EventLogStore(new FileRunRepository()),
                new GenerationEngine(),
                new StageOperationPolicy()
        );

        ReviewResult result = executor.reviewStage(
                tempDir,
                runRecord(),
                StageType.IMPLEMENTATION,
                new StageExecution(StageType.IMPLEMENTATION, StageStatus.RUNNING, 1, null, null, null, null),
                StructuredArtifactBlocks.renderJsonBlock(
                        ArtifactBlockKind.IMPLEMENTATION_STAGE_STATUS,
                        new ImplementationStageStatusPayload(false, true, java.util.List.of())
                ),
                DocumentLanguage.ZH
        );

        assertTrue(reviewerCalled.get());
        assertEquals(expected.decision(), result.decision());
        assertEquals(expected.fixMode(), result.fixMode());
        assertEquals(expected.summary(), result.summary());
        assertEquals(expected.changeRequest(), result.changeRequest());
    }

    private StageReviewer reviewerThatReturns(AtomicBoolean reviewerCalled, ReviewResult reviewResult) {
        LlmProvider provider = new LlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options, ModelRole role) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
        WorkspaceSnapshotStore snapshotStore = new WorkspaceSnapshotStore(new FileRunRepository(), new FileProjectWorkspace());
        TestExecutor testExecutor = new TestExecutor(new FileProjectWorkspace(), provider, new com.fasterxml.jackson.databind.ObjectMapper());
        return new StageReviewer(
                provider,
                snapshotStore,
                testExecutor,
                new devflow.agent.prompt.PromptTemplateCatalog(),
                new devflow.agent.i18n.LanguagePolicy(),
                new devflow.agent.loop.AgentTurnLoop()
        ) {
            @Override
            public ReviewResult review(Path projectPath, RunRecord runRecord, StageType stageType, String artifactContent) {
                reviewerCalled.set(true);
                return reviewResult;
            }
        };
    }

    private RunRecord runRecord() {
        EnumMap<StageType, StageExecution> states = new EnumMap<>(StageType.class);
        for (StageType stageType : StageType.values()) {
            states.put(stageType, new StageExecution(stageType, StageStatus.PENDING, 0, null, null, null, null));
        }
        return new RunRecord(
                UUID.randomUUID(),
                tempDir,
                "goal",
                "constraints",
                new RunConfig(Map.of(), 5),
                StageType.IMPLEMENTATION,
                RunStatus.IN_PROGRESS,
                states,
                Instant.now(),
                Instant.now()
        );
    }
}
