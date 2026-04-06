package devflow.agent.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.artifact.FileArtifactStore;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import devflow.agent.review.FixMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DiagnosisAgent {

    private static final int DEFAULT_DIAGNOSIS_THRESHOLD = 3;
    private static final int REWORK_DIAGNOSIS_THRESHOLD = 2;
    private static final Pattern ATTEMPT_PATTERN = Pattern.compile("^## attempt=(\\d+)\\b.*$");

    private final LlmProvider llmProvider;
    private final FileArtifactStore artifactStore;
    private final ObjectMapper objectMapper;

    public DiagnosisAgent(LlmProvider llmProvider, FileArtifactStore artifactStore, ObjectMapper objectMapper) {
        this.llmProvider = llmProvider;
        this.artifactStore = artifactStore;
        this.objectMapper = objectMapper;
    }

    public boolean shouldDiagnose(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            FixMode requestedMode,
            String summary,
            String changeRequest
    ) {
        String history = artifactStore.readReviewHistory(projectPath, runRecord.runId(), stageType);
        List<FailureEntry> entries = parseHistory(history);
        int threshold = thresholdFor(requestedMode);
        if (entries.size() < threshold) {
            return false;
        }
        List<FailureEntry> recentEntries = tail(entries, threshold);
        if (modelSuggestsSameIssue(runRecord, stageType, requestedMode, recentEntries, summary, changeRequest)) {
            return true;
        }
        return heuristicSuggestsSameIssue(recentEntries, summary, changeRequest, threshold);
    }

    private boolean modelSuggestsSameIssue(
            RunRecord runRecord,
            StageType stageType,
            FixMode requestedMode,
            List<FailureEntry> recentEntries,
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
        try {
            String response = llmProvider.generate(system, user, Map.of("num_predict", 160), ModelRole.DIAGNOSIS);
            SimilarityPayload payload = objectMapper.readValue(extractJson(response), SimilarityPayload.class);
            return payload.sameIssue();
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean heuristicSuggestsSameIssue(
            List<FailureEntry> recentEntries,
            String summary,
            String changeRequest,
            int threshold
    ) {
        String currentSummaryFingerprint = summaryFingerprint(summary);
        String currentChangeRequestFingerprint = changeRequestFingerprint(changeRequest);
        int repeated = 0;
        for (int index = recentEntries.size() - 1; index >= 0; index--) {
            FailureEntry entry = recentEntries.get(index);
            if (sameFailureCluster(currentSummaryFingerprint, currentChangeRequestFingerprint, entry)) {
                repeated++;
            } else {
                break;
            }
        }
        return repeated >= threshold;
    }

    public RepairBrief diagnose(
            Path projectPath,
            RunRecord runRecord,
            StageType stageType,
            FixMode requestedMode,
            String summary,
            String changeRequest
    ) {
        String history = artifactStore.readReviewHistory(projectPath, runRecord.runId(), stageType);
        List<FailureEntry> recentEntries = tail(parseHistory(history), thresholdFor(requestedMode));
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
                """.formatted(
                stageType,
                runRecord.goal(),
                runRecord.constraints(),
                blank(summary),
                blank(changeRequest),
                requestedMode == null ? FixMode.PATCH : requestedMode,
                renderEntries(recentEntries)
        );
        try {
            String response = llmProvider.generate(system, user, Map.of("num_predict", 900), ModelRole.DIAGNOSIS);
            DiagnosisPayload payload = objectMapper.readValue(extractJson(response), DiagnosisPayload.class);
            return new RepairBrief(
                    payload.failureCluster(),
                    safeList(payload.repeatedErrors()),
                    payload.rootCauseHypothesis(),
                    safeList(payload.affectedFiles()),
                    safeList(payload.evidence()),
                    payload.recommendedMode() == null ? requestedModeOrDefault(requestedMode) : payload.recommendedMode(),
                    safeList(payload.mustFixFirst()),
                    safeList(payload.forbiddenDirections()),
                    safeList(payload.doNotChange()),
                    safeList(payload.acceptanceTarget()),
                    safeList(payload.acceptanceChecks())
            );
        } catch (Exception exception) {
            return fallbackBrief(recentEntries, requestedMode, summary, changeRequest);
        }
    }

    private RepairBrief fallbackBrief(List<FailureEntry> recentEntries, FixMode requestedMode, String summary, String changeRequest) {
        List<String> repeatedErrors = new ArrayList<>();
        for (FailureEntry entry : recentEntries) {
            repeatedErrors.add(blank(entry.summary()) + " | " + firstLine(entry.changeRequest()));
        }
        return new RepairBrief(
                firstLine(summary),
                repeatedErrors,
                "连续多轮出现相同或高度相似的失败，原实现路径没有收敛，需要根据最近失败轨迹做定点修复。",
                List.of(),
                List.of(blank(summary), firstLine(changeRequest)),
                requestedModeOrDefault(requestedMode),
                List.of(firstNonBlank(summary, changeRequest)),
                List.of("不要继续沿着已失败的同一路径重复修改"),
                List.of("不要无差别重写整个功能"),
                List.of("修复后同类错误不再重复出现"),
                List.of("验证最近连续失败里反复出现的问题已经消失")
        );
    }

    private List<FailureEntry> parseHistory(String history) {
        List<FailureEntry> entries = new ArrayList<>();
        String content = history == null ? "" : history.trim();
        if (content.isBlank()) {
            return entries;
        }
        String[] blocks = content.split("(?m)^## attempt=");
        for (String rawBlock : blocks) {
            String block = rawBlock.strip();
            if (block.isBlank()) {
                continue;
            }
            String normalizedBlock = "## attempt=" + block;
            String[] lines = normalizedBlock.split("\\R");
            Matcher attemptMatcher = ATTEMPT_PATTERN.matcher(lines[0].trim());
            int attempt = attemptMatcher.find() ? Integer.parseInt(attemptMatcher.group(1)) : -1;
            String summary = "";
            String changeRequest = "";
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("- summary:")) {
                    summary = trimmed.substring("- summary:".length()).trim();
                } else if (trimmed.startsWith("- changeRequest:")) {
                    changeRequest = trimmed.substring("- changeRequest:".length()).trim();
                }
            }
            entries.add(new FailureEntry(attempt, summary, changeRequest));
        }
        return entries;
    }

    private List<FailureEntry> tail(List<FailureEntry> entries, int size) {
        if (entries.size() <= size) {
            return entries;
        }
        return entries.subList(entries.size() - size, entries.size());
    }

    private String renderEntries(List<FailureEntry> entries) {
        if (entries.isEmpty()) {
            return "(no history)";
        }
        StringBuilder builder = new StringBuilder();
        for (FailureEntry entry : entries) {
            builder.append("- attempt=").append(entry.attempt()).append('\n');
            builder.append("  summary: ").append(blank(entry.summary())).append('\n');
            builder.append("  changeRequest: ").append(blank(entry.changeRequest())).append('\n');
        }
        return builder.toString();
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("No JSON object found in diagnosis response");
        }
        return response.substring(start, end + 1);
    }

    private boolean sameFailureCluster(String currentSummaryFingerprint, String currentChangeRequestFingerprint, FailureEntry entry) {
        String entrySummaryFingerprint = summaryFingerprint(entry.summary());
        if (!currentSummaryFingerprint.isBlank() && currentSummaryFingerprint.equals(entrySummaryFingerprint)) {
            return true;
        }
        String entryChangeRequestFingerprint = changeRequestFingerprint(entry.changeRequest());
        return !currentChangeRequestFingerprint.isBlank()
                && currentChangeRequestFingerprint.equals(entryChangeRequestFingerprint);
    }

    private String summaryFingerprint(String summary) {
        return normalize(summary);
    }

    private String changeRequestFingerprint(String changeRequest) {
        return normalize(firstLine(changeRequest));
    }

    private String normalize(String value) {
        return blank(value)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    private int thresholdFor(FixMode requestedMode) {
        return requestedMode == FixMode.REWORK ? REWORK_DIAGNOSIS_THRESHOLD : DEFAULT_DIAGNOSIS_THRESHOLD;
    }

    private String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.strip().lines().findFirst().orElse("").trim();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        if (second != null && !second.isBlank()) {
            return firstLine(second);
        }
        return "优先修复最近连续失败中重复出现的核心问题";
    }

    private FixMode requestedModeOrDefault(FixMode requestedMode) {
        return requestedMode == null || requestedMode == FixMode.NONE ? FixMode.PATCH : requestedMode;
    }

    private List<String> safeList(List<String> items) {
        return items == null ? List.of() : items;
    }

    private record FailureEntry(
            int attempt,
            String summary,
            String changeRequest
    ) {
    }

    private record DiagnosisPayload(
            String failureCluster,
            List<String> repeatedErrors,
            String rootCauseHypothesis,
            List<String> affectedFiles,
            List<String> evidence,
            FixMode recommendedMode,
            List<String> mustFixFirst,
            List<String> forbiddenDirections,
            List<String> doNotChange,
            List<String> acceptanceTarget,
            List<String> acceptanceChecks
    ) {
    }

    private record SimilarityPayload(
            boolean sameIssue,
            String reason
    ) {
    }
}
