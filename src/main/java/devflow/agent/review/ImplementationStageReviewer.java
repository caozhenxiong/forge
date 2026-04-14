package devflow.agent.review;

import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ExecutionContract;
import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.gate.ArchitectIntegrationCheck;
import devflow.agent.executor.gate.ArchitectIntegrationCheckResult;
import devflow.agent.executor.testing.ExperienceFailureDisposition;
import devflow.agent.executor.testing.TestExecutor;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.LanguagePolicy;
import devflow.agent.project.WorkspaceSnapshotStore;
import java.nio.file.Path;

final class ImplementationStageReviewer {

    private final WorkspaceSnapshotStore snapshotStore;
    private final TestExecutor testExecutor;
    private final ReviewArtifactLoader reviewArtifactLoader;
    private final ContractExtractor contractExtractor;
    private final ArchitectIntegrationCheck architectIntegrationCheck;
    private final ImplementationReviewTurnExecutor implementationReviewTurnExecutor;
    private final LanguagePolicy languagePolicy;

    ImplementationStageReviewer(
            WorkspaceSnapshotStore snapshotStore,
            TestExecutor testExecutor,
            ReviewArtifactLoader reviewArtifactLoader,
            ContractExtractor contractExtractor,
            ArchitectIntegrationCheck architectIntegrationCheck,
            ImplementationReviewTurnExecutor implementationReviewTurnExecutor,
            LanguagePolicy languagePolicy
    ) {
        this.snapshotStore = snapshotStore;
        this.testExecutor = testExecutor;
        this.reviewArtifactLoader = reviewArtifactLoader;
        this.contractExtractor = contractExtractor;
        this.architectIntegrationCheck = architectIntegrationCheck;
        this.implementationReviewTurnExecutor = implementationReviewTurnExecutor;
        this.languagePolicy = languagePolicy;
    }

    ReviewResult review(Path projectPath, RunRecord runRecord, String artifactContent) {
        SelfCheckResult selfCheck = testExecutor.selfCheck(projectPath);
        if (!selfCheck.passed()) {
            return new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                    selfCheck.summary(),
                    selfCheck.details()
            );
        }
        ReviewResult contractGateResult = enforceImplementationContractGate(projectPath, runRecord);
        if (contractGateResult != null) {
            return contractGateResult;
        }
        ReviewResult targetedVerificationGate = enforceImplementationFailureVerification(projectPath, runRecord, artifactContent);
        if (targetedVerificationGate != null) {
            return targetedVerificationGate;
        }

        String changes = snapshotStore.buildReviewChangePack(projectPath, runRecord.runId()).toMarkdown();
        String candidate = artifactContent
                + "\n\n## 实现自检\n\n"
                + selfCheck.summary()
                + "\n\n"
                + selfCheck.details()
                + "\n\n## 实际代码变更\n\n"
                + changes;
        String reviewerContext = reviewArtifactLoader.readReviewerContext(runRecord);
        return implementationReviewTurnExecutor.run(runRecord, candidate, reviewerContext);
    }

    private ReviewResult enforceImplementationFailureVerification(Path projectPath, RunRecord runRecord, String artifactContent) {
        ExperienceFailureDisposition previousFailure = reviewArtifactLoader.readTestFailureDisposition(runRecord);
        if (previousFailure == null || !previousFailure.requiresImplementationReverification()) {
            return null;
        }
        String prd = reviewArtifactLoader.readStageArtifact(runRecord, StageType.PRD);
        String design = reviewArtifactLoader.readStageArtifact(runRecord, StageType.DESIGN);
        DocumentLanguage language = languagePolicy.resolve(
                artifactContent,
                runRecord.goal(),
                runRecord.constraints()
        );
        return testExecutor.verifyImplementationRepairTargets(
                projectPath,
                runRecord.goal(),
                runRecord.constraints(),
                prd,
                design,
                artifactContent,
                previousFailure,
                language
        );
    }

    private ReviewResult enforceImplementationContractGate(Path projectPath, RunRecord runRecord) {
        String prd = reviewArtifactLoader.readStageArtifact(runRecord, StageType.PRD);
        String design = reviewArtifactLoader.readStageArtifact(runRecord, StageType.DESIGN);
        ExecutionContract executionContract = contractExtractor.extractExecutionContract(
                runRecord.goal(),
                runRecord.constraints(),
                prd,
                design
        );
        ArchitectIntegrationCheckResult architectCheckResult = architectIntegrationCheck.verify(projectPath, executionContract);
        if (architectCheckResult.passed()) {
            return null;
        }
        ReviewReasonCode reasonCode = devflow.agent.executor.gate.ImplementationContractGateMessages.reasonCode(architectCheckResult);
        String summary = devflow.agent.executor.gate.ImplementationContractGateMessages.summary(architectCheckResult);
        String changeRequest = devflow.agent.executor.gate.ImplementationContractGateMessages.changeRequest(architectCheckResult);
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                summary,
                changeRequest,
                architectCheckResult.details(),
                "",
                architectCheckResult.implementationPatchTarget(),
                java.util.List.of(),
                ReviewRevisionRoute.PATCH_CURRENT_STAGE,
                reasonCode
        );
    }
}
