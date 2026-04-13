package devflow.agent.repair;

import devflow.agent.domain.RunRecord;
import devflow.agent.domain.StageType;
import devflow.agent.review.FixMode;
import java.util.List;

/**
 * diagnosis prompt 组装器。
 *
 * <p>负责相似问题判断和 repair brief 诊断的 prompt 文本，避免 `DiagnosisAgent`
 * 同时维护模型调用、历史读取和长 prompt 模板。
 */
final class DiagnosisPromptAssembler {

    DiagnosisPrompt buildSimilarityPrompt(
            RunRecord runRecord,
            StageType stageType,
            FixMode requestedMode,
            List<DiagnosisAgent.FailureEntry> recentEntries,
            String summary,
            String changeRequest
    ) {
        String system = """
                你是 FailureSimilarityJudge。你的任务是判断“当前失败”和“最近连续失败轨迹”是否属于同一个根因簇。
                你必须只返回一个 JSON 对象，不要输出任何额外解释。
                JSON 格式：
                {
                  "sameIssue": true,
                  "reason": "一句话说明"
                }

                规则：
                1. 关注根因是否相同，而不是措辞是否相同
                2. 如果只是不同表述指向同一技术缺陷，应判断为 true
                3. 如果问题已经切换到新的模块或新的失败类型，应判断为 false
                4. 不要因为 changeRequest 细节不同就直接判 false
                """;
        String user = """
                当前阶段：%s
                目标：%s
                约束：%s
                当前 fixMode：%s
                当前 summary：%s
                当前 changeRequest：%s

                最近连续失败轨迹：
                %s
                """.formatted(
                stageType,
                runRecord.goal(),
                runRecord.constraints(),
                requestedMode == null ? FixMode.PATCH : requestedMode,
                blank(summary),
                blank(changeRequest),
                renderEntries(recentEntries)
        );
        return new DiagnosisPrompt(system, user);
    }

    DiagnosisPrompt buildDiagnosisPrompt(
            RunRecord runRecord,
            StageType stageType,
            FixMode requestedMode,
            List<DiagnosisAgent.FailureEntry> recentEntries,
            String summary,
            String changeRequest,
            DiagnosisArtifactEvidence artifactEvidence
    ) {
        String system = """
                你是 DiagnosisAgent。你的任务是从连续失败的 review 轨迹里提炼出可执行的问题摘要，供 RepairAgent 使用。
                你必须只返回一个 JSON 对象，不要输出任何额外解释。
                JSON 格式：
                {
                  "failureCluster": "问题聚类名称",
                  "repeatedErrors": ["重复出现的错误1"],
                  "rootCauseHypothesis": "根因假设",
                  "affectedFiles": ["相关文件"],
                  "evidence": ["直接证据"],
                  "recommendedMode": "PATCH|REWORK",
                  "mustFixFirst": ["必须优先修复的问题"],
                  "forbiddenDirections": ["不要继续沿着这些错误方向修"],
                  "doNotChange": ["尽量不要动的部分"],
                  "acceptanceTarget": ["修复完成后必须看到的验收信号"],
                  "acceptanceChecks": ["修复后需要重新检查的关键点"]
                }

                规则：
                1. 重点提炼反复出现的问题，不要重复历史噪音
                2. 如果结构基本可接受，只给 PATCH
                3. 如果是重复实现、入口未接线、模块边界错误等结构性问题，给 REWORK
                4. 证据必须来自输入材料本身
                5. mustFixFirst 必须是 coder 这轮不修就无法收敛的关键问题
                6. forbiddenDirections 必须指出这几轮反复走偏的错误修复方向
                7. acceptanceChecks 必须是下一轮 verifier/reviewer 应优先验证的项目
                """;
        String user = """
                当前阶段：%s
                目标：%s
                约束：%s
                当前 review summary：%s
                当前 changeRequest：%s
                当前建议修复模式：%s

                最近连续失败轨迹：
                %s

                最新测试与运行证据：
                %s
                """.formatted(
                stageType,
                runRecord.goal(),
                runRecord.constraints(),
                blank(summary),
                blank(changeRequest),
                requestedMode == null ? FixMode.PATCH : requestedMode,
                renderEntries(recentEntries),
                renderArtifacts(artifactEvidence)
        );
        return new DiagnosisPrompt(system, user);
    }

    private String renderEntries(List<DiagnosisAgent.FailureEntry> entries) {
        if (entries.isEmpty()) {
            return "(no history)";
        }
        StringBuilder builder = new StringBuilder();
        for (DiagnosisAgent.FailureEntry entry : entries) {
            builder.append("- attempt=").append(entry.attempt()).append('\n');
            builder.append("  summary: ").append(blank(entry.summary())).append('\n');
            builder.append("  changeRequest: ").append(blank(entry.changeRequest())).append('\n');
            if (entry.evidence() != null && !entry.evidence().isBlank()) {
                builder.append("  evidence: ").append(entry.evidence().trim()).append('\n');
            }
            if (entry.actionItems() != null && !entry.actionItems().isBlank()) {
                builder.append("  actionItems: ").append(entry.actionItems().trim()).append('\n');
            }
        }
        return builder.toString();
    }

    private String renderArtifacts(DiagnosisArtifactEvidence artifactEvidence) {
        if (artifactEvidence == null) {
            return "(no supplemental artifacts)";
        }
        StringBuilder builder = new StringBuilder();
        appendArtifact(builder, "test_runtime_snapshot.md", artifactEvidence.testRuntimeSnapshot());
        appendArtifact(builder, "test_execution.md", artifactEvidence.testExecution());
        appendArtifact(builder, "test_report.md", artifactEvidence.testReport());
        return builder.isEmpty() ? "(no supplemental artifacts)" : builder.toString().trim();
    }

    private void appendArtifact(StringBuilder builder, String fileName, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append("[").append(fileName).append("]\n").append(content.trim());
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
