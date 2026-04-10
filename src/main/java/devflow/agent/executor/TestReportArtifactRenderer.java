package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.ReviewArtifactPayload;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.quality.CoverageLedger;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;

/**
 * 负责测试阶段最终报告的 markdown 渲染。
 */
final class TestReportArtifactRenderer {

    String render(
            String note,
            CollectedTestEvidence evidence,
            TestEvidenceGateOutcome evidenceOutcome,
            DocumentLanguage language
    ) {
        SelfCheckResult selfCheck = evidence.selfCheck();
        ArchitectIntegrationCheckResult architectCheck = evidence.architectCheck();
        long totalCases = evidenceOutcome.totalCases();
        long passedCases = evidenceOutcome.passedCases();
        long requiredFailedCases = evidenceOutcome.requiredFailedCases();
        long requiredBlockedCases = evidenceOutcome.requiredBlockedCases();
        CoverageLedger coverageLedger = evidence.coverageLedger();
        boolean finalPassed = evidenceOutcome.finalPassed();
        String summary = evidenceOutcome.summary();
        String machineBlock = StructuredArtifactBlocks.renderJsonBlock(
                ArtifactBlockKind.REVIEW_RESULT,
                new ReviewArtifactPayload(
                        (finalPassed ? ReviewDecision.APPROVED : ReviewDecision.REJECTED).name(),
                        (finalPassed ? FixMode.NONE : FixMode.PATCH).name(),
                        summary,
                        finalPassed ? "" : language.choose("修复失败或阻塞的必测用例并重新执行测试。", "Fix failing or blocked required cases and rerun testing."),
                        "",
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
        return """
                %s

                %s

                # %s

                - decision: %s
                - note: %s
                - summary: %s
                - selfCheckPassed: %s
                - architectCheckPassed: %s
                - totalCases: %d
                - passedCases: %d
                - requiredFailedCases: %d
                - requiredBlockedCases: %d

                ## %s

                %s
                
                ## %s

                %s
        """.formatted(
                machineBlock,
                qualityLedgerBlock,
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
                coverageLedger == null ? language.choose("- 无覆盖账本", "- No coverage ledger") : coverageLedger.toMarkdown(language)
        );
    }
}
