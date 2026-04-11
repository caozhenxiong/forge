package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ReviewArtifactPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.quality.QualityLedger;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;

/**
 * 负责测试阶段最终报告的 markdown 渲染。
 */
final class TestReportArtifactRenderer {

    String render(
            String note,
            CollectedTestEvidence evidence,
            TestEvidenceGateOutcome evidenceOutcome,
            UiRuntimeContract runtimeContract,
            ExperienceFailureDisposition disposition,
            DocumentLanguage language
    ) {
        SelfCheckResult selfCheck = evidence.selfCheck();
        ArchitectIntegrationCheckResult architectCheck = evidence.architectCheck();
        long totalCases = evidenceOutcome.totalCases();
        long passedCases = evidenceOutcome.passedCases();
        long requiredFailedCases = evidenceOutcome.requiredFailedCases();
        long requiredBlockedCases = evidenceOutcome.requiredBlockedCases();
        QualityLedger qualityLedger = evidence.qualityLedger();
        boolean finalPassed = evidenceOutcome.finalPassed();
        ExperienceFailureDisposition effectiveDisposition = disposition == null ? ExperienceFailureDisposition.pass() : disposition;
        String summary = finalPassed ? evidenceOutcome.summary() : effectiveDisposition.summary().isBlank()
                ? evidenceOutcome.summary()
                : effectiveDisposition.summary();
        String machineBlock = StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.REVIEW_RESULT,
                new ReviewArtifactPayload(
                        (finalPassed ? ReviewDecision.APPROVED : ReviewDecision.REJECTED).name(),
                        (finalPassed ? FixMode.NONE : FixMode.PATCH).name(),
                        (finalPassed ? ImplementationPatchTarget.NONE : effectiveDisposition.implementationPatchTarget()).name(),
                        (finalPassed ? java.util.List.<devflow.agent.protocol.FileChangePayload>of() : effectiveDisposition.overrideChanges().stream()
                                .map(change -> new devflow.agent.protocol.FileChangePayload(
                                        change.path(),
                                        change.action() == null ? "" : change.action().name(),
                                        change.reason(),
                                        change.editScope() == null ? "" : change.editScope().name(),
                                        change.runtimeOwnership() == null ? "" : change.runtimeOwnership().name(),
                                        change.hostHtmlPatchRequired()
                                ))
                                .toList()),
                        (finalPassed ? devflow.agent.review.ReviewRevisionRoute.PATCH_CURRENT_STAGE : effectiveDisposition.revisionRoute()).name(),
                        (finalPassed ? devflow.agent.review.ReviewReasonCode.NONE : effectiveDisposition.reasonCode()).name(),
                        summary,
                        finalPassed ? "" : effectiveDisposition.changeRequest(),
                        finalPassed ? "" : effectiveDisposition.evidence(),
                        "",
                        !finalPassed,
                        Math.toIntExact(requiredFailedCases + requiredBlockedCases),
                        finalPassed ? 0 : 1
                )
        );
        String qualityLedgerBlock = StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.QUALITY_LEDGER,
                evidence.qualityLedger()
        );
        String runtimeContractBlock = StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.UI_RUNTIME_CONTRACT,
                runtimeContract == null ? UiRuntimeContract.empty() : runtimeContract
        );
        String dispositionBlock = StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.EXPERIENCE_FAILURE_DISPOSITION,
                effectiveDisposition
        );
        return """
                %s

                %s

                %s

                %s

                # %s

                决策：%s
                备注：%s
                总结：%s
                自检通过：%s
                架构检查通过：%s
                总用例数：%d
                已通过：%d
                必测失败：%d
                必测阻塞：%d

                ## %s

                %s
                
                ## %s

                %s
        """.formatted(
                machineBlock,
                qualityLedgerBlock,
                runtimeContractBlock,
                dispositionBlock,
                language.choose("测试报告", "Test Report"),
                (finalPassed ? ReviewDecision.APPROVED : ReviewDecision.REJECTED).name(),
                note,
                summary,
                selfCheck.passed(),
                architectCheck == null || architectCheck.passed(),
                totalCases,
                passedCases,
                requiredFailedCases,
                requiredBlockedCases,
                language.choose("结论", "Conclusion"),
                requiredFailedCases == 0 && requiredBlockedCases == 0
                        ? language.choose("所有必测 test case 已通过。", "All required test cases passed.")
                        : language.choose("仍有必测 test case 未通过或未执行，不能视为测试完成。", "Required test cases are still failing or blocked, so testing cannot be considered complete."),
                language.choose("覆盖账本", "Coverage Ledger"),
                qualityLedger == null ? language.choose("- 无覆盖账本", "- No coverage ledger") : qualityLedger.toMarkdown(language)
        );
    }
}
