package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolFailureCode;
import devflow.agent.executor.tools.ToolResult;

import java.nio.file.Path;

import devflow.agent.executor.implementation.ImplementationEventJournal;
import devflow.agent.executor.implementation.ImplementationEventMessages;
/**
 * 本地校验失败后的 syntax repair 链。
 */
final class SyntaxRepairSupport {

    private final PatchRepairClassifier patchRepairClassifier;
    private final DeterministicSyntaxRepairer deterministicSyntaxRepairer;
    private final SyntaxRepairTurn syntaxRepairTurn;
    private final PatchRepairSettings patchRepairSettings;
    private final PatchVerifier patchVerifier;
    private final RepairDiffScopeValidator repairDiffScopeValidator;
    private final PatchExecutionSupport executionSupport;

    SyntaxRepairSupport(
            PatchRepairClassifier patchRepairClassifier,
            DeterministicSyntaxRepairer deterministicSyntaxRepairer,
            SyntaxRepairTurn syntaxRepairTurn,
            PatchRepairSettings patchRepairSettings,
            PatchVerifier patchVerifier,
            RepairDiffScopeValidator repairDiffScopeValidator,
            PatchExecutionSupport executionSupport
    ) {
        this.patchRepairClassifier = patchRepairClassifier;
        this.deterministicSyntaxRepairer = deterministicSyntaxRepairer;
        this.syntaxRepairTurn = syntaxRepairTurn;
        this.patchRepairSettings = patchRepairSettings;
        this.patchVerifier = patchVerifier;
        this.repairDiffScopeValidator = repairDiffScopeValidator;
        this.executionSupport = executionSupport;
    }

    PatchApplyResult repairCodeFile(
            CodeTargetedRewriteRequest request,
            String baselineContent,
            EditUnit unit,
            PatchFailure patchFailure,
            PatchApplyResult applyResult
    ) {
        return repair(
                request.relativePath(),
                unit,
                patchFailure,
                applyResult,
                request.eventJournal(),
                (candidateContent, repairedContent) -> validateRepairedCodeFile(request, baselineContent, candidateContent, repairedContent)
        );
    }

    PatchApplyResult repairEmbedded(
            EmbeddedTargetedRewriteRequest request,
            EmbeddedPatchKind patchKind,
            String baselineContent,
            EditUnit unit,
            PatchFailure patchFailure,
            PatchApplyResult applyResult
    ) {
        return repair(
                request.relativePath(),
                unit,
                patchFailure,
                applyResult,
                request.eventJournal(),
                (candidateContent, repairedContent) -> validateRepairedEmbeddedContent(request, patchKind, baselineContent, candidateContent, repairedContent)
        );
    }

    private PatchApplyResult repair(
            Path relativePath,
            EditUnit unit,
            PatchFailure patchFailure,
            PatchApplyResult applyResult,
            ImplementationEventJournal eventJournal,
            ContentVerifier verifier
    ) {
        if (applyResult == null || applyResult.content() == null || !patchRepairClassifier.supportsSyntaxRepair(patchFailure)) {
            return applyResult;
        }
        String candidateContent = applyResult.content();
        String evidence = patchFailure == null ? "" : patchFailure.evidence();
        ToolResult latestFailureResult = applyResult.failureResult();
        String deterministicCandidate = deterministicSyntaxRepairer.repair(candidateContent);
        if (deterministicCandidate != null && !deterministicCandidate.equals(candidateContent)) {
            executionSupport.appendImplementationEvent(
                    eventJournal,
                    ImplementationEventMessages.repairTrace(
                            "syntax-repair",
                            relativePath,
                            unit.label(),
                            "deterministic-applied",
                            evidence
                    )
            );
            ToolResult verifyResult = verifier.verify(candidateContent, deterministicCandidate);
            executionSupport.appendImplementationEvent(
                    eventJournal,
                    ImplementationEventMessages.repairTrace(
                            "repair-validate",
                            relativePath,
                            unit.label(),
                            repairValidationResultLabel(verifyResult),
                            verifyResult.evidence()
                    )
            );
            if (verifyResult.succeeded()) {
                return new PatchApplyResult(deterministicCandidate, applyResult.applyResult(), verifyResult);
            }
            if (shouldStopRepairLoop(verifyResult)) {
                return new PatchApplyResult(deterministicCandidate, applyResult.applyResult(), verifyResult);
            }
            candidateContent = deterministicCandidate;
            evidence = verifyResult.evidence();
            latestFailureResult = verifyResult;
        }
        for (int attempt = 1; attempt <= patchRepairSettings.syntaxModelRepairAttempts(); attempt++) {
            executionSupport.appendImplementationEvent(
                    eventJournal,
                    ImplementationEventMessages.repairTrace(
                            "syntax-repair",
                            relativePath,
                            unit.label(),
                            "model-attempt-" + attempt,
                            evidence
                    )
            );
            try {
                String repairedContent = syntaxRepairTurn.repair(relativePath, unit, candidateContent, evidence);
                ToolResult verifyResult = verifier.verify(candidateContent, repairedContent);
                executionSupport.appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.repairTrace(
                                "repair-validate",
                                relativePath,
                                unit.label(),
                                repairValidationResultLabel(verifyResult),
                                verifyResult.evidence()
                        )
                );
                if (verifyResult.succeeded()) {
                    return new PatchApplyResult(repairedContent, applyResult.applyResult(), verifyResult);
                }
                if (shouldStopRepairLoop(verifyResult)) {
                    return new PatchApplyResult(repairedContent, applyResult.applyResult(), verifyResult);
                }
                candidateContent = repairedContent;
                evidence = verifyResult.evidence();
                latestFailureResult = verifyResult;
            } catch (Exception repairFailure) {
                evidence = repairFailure.getMessage() == null
                        ? repairFailure.getClass().getSimpleName()
                        : repairFailure.getMessage();
                executionSupport.appendImplementationEvent(
                        eventJournal,
                        ImplementationEventMessages.repairTrace(
                                "syntax-repair",
                                relativePath,
                                unit.label(),
                                "model-failed",
                                evidence
                        )
                );
            }
        }
        writeSyntaxFailureArtifact(eventJournal, relativePath, unit, evidence, candidateContent, latestFailureResult);
        return new PatchApplyResult(candidateContent, applyResult.applyResult(), latestFailureResult);
    }

    private ToolResult validateRepairedCodeFile(
            CodeTargetedRewriteRequest request,
            String baselineContent,
            String candidateContent,
            String repairedContent
    ) {
        ToolResult verifyResult = patchVerifier.verifyCodeFile(request.projectPath(), request.relativePath(), repairedContent);
        if (!verifyResult.succeeded()) {
            return verifyResult;
        }
        return repairDiffScopeValidator.verify(baselineContent, candidateContent, repairedContent);
    }

    private ToolResult validateRepairedEmbeddedContent(
            EmbeddedTargetedRewriteRequest request,
            EmbeddedPatchKind patchKind,
            String baselineContent,
            String candidateContent,
            String repairedContent
    ) {
        ToolResult verifyResult = patchKind == EmbeddedPatchKind.SCRIPT
                ? patchVerifier.verifyInlineScript(request.relativePath(), repairedContent)
                : patchVerifier.verifyInlineStyle(request.relativePath(), repairedContent);
        if (!verifyResult.succeeded()) {
            return verifyResult;
        }
        return repairDiffScopeValidator.verify(baselineContent, candidateContent, repairedContent);
    }

    private boolean shouldStopRepairLoop(ToolResult verifyResult) {
        return verifyResult != null && verifyResult.failureCode() == ToolFailureCode.TARGET_SCOPE_VIOLATION;
    }

    private String repairValidationResultLabel(ToolResult verifyResult) {
        if (verifyResult == null) {
            return "validate-failed";
        }
        if (verifyResult.succeeded()) {
            return "validated";
        }
        return switch (verifyResult.failureCode()) {
            case HTML_STRUCTURE_INVALID, JAVASCRIPT_STRUCTURE_INVALID, INLINE_SCRIPT_INVALID -> "structure-failed";
            case SYNTAX_INVALID, JAVASCRIPT_SYNTAX_INVALID -> "syntax-failed";
            default -> "validate-failed";
        };
    }

    private void writeSyntaxFailureArtifact(
            ImplementationEventJournal eventJournal,
            Path relativePath,
            EditUnit unit,
            String evidence,
            String candidateContent,
            ToolResult latestFailureResult
    ) {
        if (!isSyntaxFailure(latestFailureResult)) {
            return;
        }
        Path artifactPath = eventJournal == null
                ? null
                : eventJournal.appendSyntaxRepairFailureArtifact(relativePath, unit.label(), evidence, candidateContent);
        if (artifactPath == null) {
            return;
        }
        executionSupport.appendImplementationEvent(
                eventJournal,
                ImplementationEventMessages.repairTrace(
                        "syntax-failure-artifact",
                        relativePath,
                        unit.label(),
                        "written",
                        artifactPath.getFileName().toString()
                )
        );
    }

    private boolean isSyntaxFailure(ToolResult verifyResult) {
        if (verifyResult == null || verifyResult.succeeded()) {
            return false;
        }
        return switch (verifyResult.failureCode()) {
            case SYNTAX_INVALID, JAVASCRIPT_SYNTAX_INVALID -> true;
            default -> false;
        };
    }

    @FunctionalInterface
    private interface ContentVerifier {
        ToolResult verify(String candidateContent, String repairedContent);
    }
}
