package devflow.agent.review;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.FileEditScope;
import devflow.agent.executor.RuntimeOwnershipMode;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.FileChangePayload;
import devflow.agent.protocol.ReviewArtifactPayload;
import devflow.agent.protocol.ReviewArtifactPayloadSupport;
import devflow.agent.text.TextCanonicalizer;
import devflow.agent.util.EnumParsers;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析阶段产物中自带的 decision 元数据，并做最小确定性归一。
 *
 * <p>这部分逻辑本质上是程序化解析与护栏，不应该继续塞在
 * StageReviewer 的语义审查职责里。
 */
class ReviewDecisionArtifactParser {

    ReviewResult parseDecisionArtifact(String artifactContent, ReviewDecision defaultDecision, String defaultChangeRequest) {
        ReviewArtifactPayload payload = ReviewArtifactPayloadSupport.readFirstPayload(artifactContent);
        if (payload != null && payload.decision() != null && !payload.decision().isBlank()) {
            ReviewDecision decision = EnumParsers.parseIgnoreCase(
                    ReviewDecision.class,
                    payload.decision(),
                    defaultDecision
            );
            FixMode fixMode = payload.fixMode() == null || payload.fixMode().isBlank()
                    ? (decision == ReviewDecision.APPROVED ? FixMode.NONE : FixMode.PATCH)
                    : EnumParsers.parseIgnoreCase(FixMode.class, payload.fixMode(), FixMode.PATCH);
            ImplementationPatchTarget patchTarget = EnumParsers.parseIgnoreCase(
                    ImplementationPatchTarget.class,
                    payload.implementationPatchTarget(),
                    ImplementationPatchTarget.NONE
            );
            ReviewRevisionRoute revisionRoute = EnumParsers.parseIgnoreCase(
                    ReviewRevisionRoute.class,
                    payload.revisionRoute(),
                    ReviewRevisionRoute.PATCH_CURRENT_STAGE
            );
            ReviewReasonCode reasonCode = EnumParsers.parseIgnoreCase(
                    ReviewReasonCode.class,
                    payload.reasonCode(),
                    ReviewReasonCode.NONE
            );
            List<FileChange> overrideChanges = parseOverrideChanges(payload.overrideChanges());
            String summary = blankOrDefault(payload.summary(), "来自阶段产物的审阅结论。");
            // 即使 decision=APPROVED，也要先完整保留结构化字段。
            // 如果后续发现 blockingFindings=true，需要用这些字段组装降级后的证据。
            String rawChangeRequest = blankOrDefault(payload.changeRequest(), defaultChangeRequest);
            String evidence = blankOrDefault(payload.evidence(), "");
            String actionItems = blankOrDefault(payload.actionItems(), "");
            if (decision == ReviewDecision.APPROVED && hasStructuredBlockingFindings(payload)) {
                List<String> findings = extractStructuredFindings(artifactContent, summary, rawChangeRequest, evidence, actionItems);
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        buildFindingsSummary(summary, findings),
                        buildFindingsChangeRequest(rawChangeRequest, findings),
                        buildFindingsEvidence(evidence, findings),
                        buildFindingsActionItems(actionItems, findings),
                        patchTarget,
                        overrideChanges,
                        revisionRoute,
                        reasonCode
                );
            }
            String changeRequest = decision == ReviewDecision.APPROVED ? "" : rawChangeRequest;
            return new ReviewResult(
                    decision,
                    fixMode,
                    summary,
                    changeRequest,
                    evidence,
                    actionItems,
                    patchTarget,
                    overrideChanges,
                    revisionRoute,
                    reasonCode
            );
        }
        return new ReviewResult(
                defaultDecision,
                defaultDecision == ReviewDecision.APPROVED ? FixMode.NONE : FixMode.PATCH,
                "阶段产物未给出明确 decision。",
                defaultChangeRequest
        );
    }

    private String blankOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private boolean hasStructuredBlockingFindings(ReviewArtifactPayload payload) {
        if (payload.blockingFindings() != null) {
            return payload.blockingFindings();
        }
        return payload.findingCount() != null && payload.findingCount() > 0;
    }

    private List<String> extractStructuredFindings(String artifactContent, String summary, String changeRequest, String evidence, String actionItems) {
        List<String> findings = new ArrayList<>();
        if (summary != null && !summary.isBlank()) {
            findings.add(summary);
        }
        if (changeRequest != null && !changeRequest.isBlank()) {
            findings.add(changeRequest);
        }
        if (evidence != null && !evidence.isBlank()) {
            findings.add(evidence);
        }
        if (actionItems != null && !actionItems.isBlank()) {
            findings.add(actionItems);
        }
        if (findings.isEmpty()) {
            findings.add("blockingFindings=true，但审阅产物没有给出对应的 summary/changeRequest/evidence/actionItems。");
        }
        return findings;
    }

    private String buildFindingsSummary(String summary, List<String> findings) {
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        if (findings.isEmpty()) {
            return "审阅结论与 Findings 冲突，仍存在待修复问题。";
        }
        String first = trimFinding(findings.getFirst(), 40);
        if (findings.size() == 1) {
            return "代码审阅发现问题：" + first;
        }
        return "代码审阅发现问题：" + first + " 等 " + findings.size() + " 项。";
    }

    private String buildFindingsChangeRequest(String changeRequest, List<String> findings) {
        if (changeRequest != null && !changeRequest.isBlank()) {
            return changeRequest;
        }
        if (findings.isEmpty()) {
            return "Findings 中仍列出明确问题，请修复后重新进行 code review。";
        }
        StringBuilder builder = new StringBuilder("请优先修复以下问题：");
        int limit = Math.min(2, findings.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append("；");
            }
            builder.append(trimFinding(findings.get(index), 80));
        }
        if (findings.size() > limit) {
            builder.append("；其余 findings 也需一并清理");
        }
        return builder.toString();
    }

    private String buildFindingsEvidence(String evidence, List<String> findings) {
        if (evidence != null && !evidence.isBlank()) {
            return evidence;
        }
        if (findings.isEmpty()) {
            return "";
        }
        return trimFinding(findings.getFirst(), 160);
    }

    private String buildFindingsActionItems(String actionItems, List<String> findings) {
        if (actionItems != null && !actionItems.isBlank()) {
            return actionItems;
        }
        if (findings.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(3, findings.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append("；");
            }
            builder.append("修复：").append(trimFinding(findings.get(index), 90));
        }
        return builder.toString();
    }

    private String trimFinding(String finding, int limit) {
        String normalized = TextCanonicalizer.collapseWhitespace(finding);
        return normalized.length() > limit ? normalized.substring(0, limit) : normalized;
    }

    private List<FileChange> parseOverrideChanges(List<FileChangePayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }
        List<FileChange> resolved = new ArrayList<>();
        for (FileChangePayload payload : payloads) {
            if (payload == null || payload.path() == null || payload.path().isBlank()) {
                continue;
            }
            resolved.add(new FileChange(
                    payload.path(),
                    EnumParsers.parseIgnoreCase(ChangeAction.class, payload.action(), ChangeAction.WRITE),
                    payload.reason() == null ? "" : payload.reason(),
                    EnumParsers.parseIgnoreCase(FileEditScope.class, payload.editScope(), FileEditScope.AUTO),
                    EnumParsers.parseIgnoreCase(RuntimeOwnershipMode.class, payload.runtimeOwnership(), null),
                    Boolean.TRUE.equals(payload.hostHtmlPatchRequired())
            ));
        }
        return List.copyOf(resolved);
    }
}
