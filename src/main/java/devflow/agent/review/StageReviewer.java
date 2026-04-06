package devflow.agent.review;

import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.TestExecutor;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import devflow.agent.project.WorkspaceSnapshotStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StageReviewer {

    private static final Pattern DECISION_PATTERN = Pattern.compile("(?m)^- decision: (APPROVED|REVISION_REQUIRED|REJECTED)\\s*$");
    private static final Pattern FIX_MODE_PATTERN = Pattern.compile("(?m)^- fixMode: (NONE|PATCH|REWORK)\\s*$");
    private static final Pattern SUMMARY_PATTERN = Pattern.compile("(?m)^- summary: ?(.*)$");
    private static final Pattern CHANGE_REQUEST_PATTERN = Pattern.compile("(?m)^- changeRequest: ?(.*)$");
    private static final Pattern EVIDENCE_PATTERN = Pattern.compile("(?m)^- evidence: ?(.*)$");
    private static final Pattern ACTION_ITEMS_PATTERN = Pattern.compile("(?m)^- actionItems: ?(.*)$");
    private static final Pattern EXIT_CODE_PATTERN = Pattern.compile("(?m)^- exitCode: (\\d+)\\s*$");
    private static final Pattern FINDINGS_SECTION_PATTERN = Pattern.compile("(?s)## Findings\\s*(.*)$");
    private static final Pattern FINDINGS_ITEM_PATTERN = Pattern.compile("(?m)^\\s*(?:[-*]|\\d+\\.)\\s+(.+)$");
    private static final Pattern PERFORMANCE_CLAIM_PATTERN = Pattern.compile("(性能|耗时|500ms|500 ms|毫秒|速度太慢|未达标|benchmark|基准)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEASUREMENT_EVIDENCE_PATTERN = Pattern.compile("(\\d+\\s*(ms|毫秒|s|秒)|耗时|benchmark|基准|measured|timing|计时)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANALYSIS_DOWNSTREAM_DETAIL_PATTERN = Pattern.compile(
            "(算法(细节|设计|实现)|唯一解|回溯|性能指标|性能验证|量化标准|原型|线框图|交互原型|页面原型|benchmark|基准测试|测试方案细节|实现方案细节)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ANALYSIS_CORE_GAP_PATTERN = Pattern.compile(
            "(问题定义|目标|成功标准|约束|边界|非目标|风险|待确认|假设|用户场景|使用场景|范围|调研)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRD_DOWNSTREAM_DETAIL_PATTERN = Pattern.compile(
            "(算法(细节|设计|实现)|唯一解|回溯|数据结构|模块划分|类图|接口设计|实现方案|性能测试方案|benchmark|基准测试|技术选型细节|数据库表|API 细节|接口参数)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRD_CORE_GAP_PATTERN = Pattern.compile(
            "(产品目标|用户场景|功能范围|验收标准|边界条件|非功能|可用性|交互|不做什么|回退|标记|候选数|错误提示|完成判定|规格切换)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRUNCATED_TAIL_PATTERN = Pattern.compile(
            "(?m)^(?!#).*(?:与|及|或|并|、|：|:|，|,|为|是|包括|支持|提供|用于|并支持|例如|如)$"
    );
    private static final Pattern SECOND_LEVEL_HEADING_PATTERN = Pattern.compile("(?m)^##\\s+[^\\n]+$");
    private static final Pattern VALID_SECOND_LEVEL_HEADING_FORMAT_PATTERN = Pattern.compile("^##\\s+\\d+\\.\\s+.+$");
    private static final Pattern DESIGN_PERFORMANCE_REQUIREMENT_PATTERN = Pattern.compile(
            "(\\d+\\s*(ms|毫秒|s|秒)|p95|benchmark|基准|基准测试|性能验收|性能要求|记录\\s*.*耗时|测量\\s*.*耗时|生成时间\\s*(小于|低于|<)|响应时间\\s*(小于|低于|<)|切换时间\\s*(小于|低于|<))",
            Pattern.CASE_INSENSITIVE
    );
    private final LlmProvider llmProvider;
    private final WorkspaceSnapshotStore snapshotStore;
    private final TestExecutor testExecutor;

    public StageReviewer(LlmProvider llmProvider, WorkspaceSnapshotStore snapshotStore, TestExecutor testExecutor) {
        this.llmProvider = llmProvider;
        this.snapshotStore = snapshotStore;
        this.testExecutor = testExecutor;
    }

    public ReviewResult review(Path projectPath, RunRecord runRecord, StageType stageType, String artifactContent) {
        return switch (stageType) {
            case ANALYSIS -> reviewDocument(
                    stageType,
                    """
                    你是资深需求评审，请检查需求分析是否完整、清晰、可执行。
                    只审需求分析阶段应该承担的内容：
                    1. 问题定义是否清楚。
                    2. 目标、成功标准、关键约束是否明确。
                    3. 边界、非目标、风险与待确认问题是否覆盖。
                    4. 初步调研与假设是否足以支撑进入 PRD。
                    不要把以下内容当成 ANALYSIS 阶段的阻塞项：
                    - 算法实现细节
                    - 唯一解验证机制细节
                    - 量化性能测试方案
                    - 页面原型或交互原型细节
                    这些属于 DESIGN 或 TEST_CASE 阶段。
                    """,
                    artifactContent,
                    "需求分析"
            );
            case PRD -> reviewDocument(
                    stageType,
                    """
                    你是资深产品评审，请检查 PRD 是否具体、可验收、边界清晰。
                    只审 PRD 阶段应该承担的内容：
                    1. 产品目标是否明确。
                    2. 目标用户与使用场景是否清楚。
                    3. 功能范围、交互要求、边界条件是否完整。
                    4. 验收标准是否可执行。
                    5. 不做什么是否明确。
                    不要把以下内容当成 PRD 阶段的阻塞项：
                    - 算法实现细节
                    - 唯一解验证机制实现
                    - 模块划分、接口设计、类图
                    - 性能 benchmark 或技术级测试方案
                    这些属于 DESIGN 或 TEST_CASE 阶段。
                    """,
                    artifactContent,
                    "PRD"
            );
            case DESIGN -> reviewDocument(
                    stageType,
                    "你是资深架构评审，请检查技术方案是否可实施、风险是否说明充分。",
                    artifactContent,
                    "技术方案"
            );
            case IMPLEMENTATION -> reviewImplementation(projectPath, runRecord, artifactContent);
            case CODE_REVIEW -> parseDecisionArtifact(artifactContent, ReviewDecision.REVISION_REQUIRED, "代码审阅发现问题，需要修改。");
            case TEST -> reviewTestArtifact(artifactContent);
        };
    }

    private ReviewResult reviewDocument(StageType stageType, String systemPrompt, String candidateContent, String artifactLabel) {
        // 文档生成模型更适合铺陈内容，但结构化 JSON 审阅更依赖稳定的 review/coder 模型。
        ReviewResult reviewResult = reviewByModel(systemPrompt, candidateContent, ModelRole.CODE_REVIEW, artifactLabel);
        ReviewResult normalized = normalizeDocumentReview(stageType, reviewResult);
        return enforceDocumentStructure(stageType, candidateContent, artifactLabel, normalized);
    }

    private ReviewResult reviewByModel(String systemPrompt, String candidateContent, ModelRole role, String artifactLabel) {
        return llmProvider.review(systemPrompt, candidateContent, Map.of("num_predict", 240), role);
    }

    private ReviewResult reviewImplementation(Path projectPath, RunRecord runRecord, String artifactContent) {
        SelfCheckResult selfCheck = testExecutor.selfCheck(projectPath);
        if (!selfCheck.passed()) {
            return new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                    selfCheck.summary(),
                    selfCheck.details()
            );
        }

        String changes = snapshotStore.renderChanges(projectPath, runRecord.runId(), 8, 5000);
        String candidate = artifactContent
                + "\n\n## 实现自检\n\n"
                + selfCheck.summary()
                + "\n\n"
                + selfCheck.details()
                + "\n\n## 实际代码变更\n\n"
                + changes;
        ReviewResult raw = llmProvider.review(
                """
                你是严格的软件工程评审，请判断这次实现是否真正落地、是否有明显遗漏或危险改动。
                若实现报告中的子任务状态与实际代码变更、自检结果冲突，以实际代码和自检结果为准，不要因为报告里旧的 failed 标记而直接拒绝。
                约束：
                1. 只能基于输入中的明确证据下结论，不要脑补不存在的问题。
                2. 先检查功能正确性、结构一致性、入口接线和现有代码中的确定性缺陷，再考虑性能风险。
                3. 如果要给出“性能不达标”“超过 500ms”“速度太慢”这类结论，必须引用自检结果或测试结果中的明确测量证据。
                4. 如果没有测量数据，只能写“存在性能风险”或“缺少性能验证”，不能直接判定未达验收指标。
                5. changeRequest 必须具体到文件、函数、模块、测试或验证动作，不能只给空泛重构建议。
                6. actionItems 必须是 coder 可以直接执行的分步动作。
                7. 只有在结构明显错误、重复实现、入口未接线、模块边界混乱时才使用 REWORK；其余优先 PATCH。
                """,
                candidate,
                Map.of("num_predict", 260),
                ModelRole.CODE_REVIEW
        );
        return normalizeImplementationReview(raw, readStageArtifact(runRecord, StageType.DESIGN));
    }

    private ReviewResult reviewTestArtifact(String artifactContent) {
        Matcher decisionMatcher = DECISION_PATTERN.matcher(artifactContent);
        if (decisionMatcher.find()) {
            ReviewDecision decision = ReviewDecision.valueOf(decisionMatcher.group(1));
            if (decision == ReviewDecision.APPROVED) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "测试通过。", "");
            }
            return new ReviewResult(decision, FixMode.PATCH, "测试失败。", "修复失败测试并重新执行。");
        }
        Matcher exitCodeMatcher = EXIT_CODE_PATTERN.matcher(artifactContent);
        int exitCode = exitCodeMatcher.find() ? Integer.parseInt(exitCodeMatcher.group(1)) : 1;
        if (exitCode == 0) {
            return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "测试通过。", "");
        }
        return new ReviewResult(ReviewDecision.REJECTED, FixMode.PATCH, "测试失败。", "修复失败测试并重新执行。");
    }

    private ReviewResult parseDecisionArtifact(String artifactContent, ReviewDecision defaultDecision, String defaultChangeRequest) {
        Matcher matcher = DECISION_PATTERN.matcher(artifactContent);
        if (matcher.find()) {
            ReviewDecision decision = ReviewDecision.valueOf(matcher.group(1));
            Matcher fixModeMatcher = FIX_MODE_PATTERN.matcher(artifactContent);
            FixMode fixMode = fixModeMatcher.find()
                    ? FixMode.valueOf(fixModeMatcher.group(1))
                    : (decision == ReviewDecision.APPROVED ? FixMode.NONE : FixMode.PATCH);
            String summary = extractLine(SUMMARY_PATTERN, artifactContent, "来自阶段产物的审阅结论。");
            String changeRequest = decision == ReviewDecision.APPROVED
                    ? ""
                    : extractLine(CHANGE_REQUEST_PATTERN, artifactContent, defaultChangeRequest);
            String evidence = extractLine(EVIDENCE_PATTERN, artifactContent, "");
            String actionItems = extractLine(ACTION_ITEMS_PATTERN, artifactContent, "");
            if (decision == ReviewDecision.APPROVED && hasBlockingFindings(artifactContent)) {
                List<String> findings = extractBlockingFindings(artifactContent);
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        buildFindingsSummary(findings),
                        buildFindingsChangeRequest(findings),
                        buildFindingsEvidence(findings),
                        buildFindingsActionItems(findings)
                );
            }
            return new ReviewResult(decision, fixMode, summary, changeRequest, evidence, actionItems);
        }
        return new ReviewResult(defaultDecision, defaultDecision == ReviewDecision.APPROVED ? FixMode.NONE : FixMode.PATCH, "阶段产物未给出明确 decision。", defaultChangeRequest);
    }

    private String extractLine(Pattern pattern, String content, String defaultValue) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1).trim() : defaultValue;
    }

    private boolean hasBlockingFindings(String artifactContent) {
        return !extractBlockingFindings(artifactContent).isEmpty();
    }

    private List<String> extractBlockingFindings(String artifactContent) {
        List<String> findings = new ArrayList<>();
        Matcher sectionMatcher = FINDINGS_SECTION_PATTERN.matcher(artifactContent);
        if (!sectionMatcher.find()) {
            return findings;
        }
        String findingsBody = sectionMatcher.group(1);
        Matcher itemMatcher = FINDINGS_ITEM_PATTERN.matcher(findingsBody);
        while (itemMatcher.find()) {
            String item = itemMatcher.group(1).trim();
            if (item.isBlank()) {
                continue;
            }
            if (item.contains("无阻塞问题") || item.contains("无问题") || item.contains("无需修改")) {
                continue;
            }
            findings.add(item);
        }
        return findings;
    }

    private String buildFindingsSummary(List<String> findings) {
        if (findings.isEmpty()) {
            return "审阅结论与 Findings 冲突，仍存在待修复问题。";
        }
        String first = trimFinding(findings.getFirst(), 40);
        if (findings.size() == 1) {
            return "代码审阅发现问题：" + first;
        }
        return "代码审阅发现问题：" + first + " 等 " + findings.size() + " 项。";
    }

    private String buildFindingsChangeRequest(List<String> findings) {
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

    private String buildFindingsEvidence(List<String> findings) {
        if (findings.isEmpty()) {
            return "";
        }
        return trimFinding(findings.getFirst(), 160);
    }

    private String buildFindingsActionItems(List<String> findings) {
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
        String normalized = finding.replaceAll("\\s+", " ").trim();
        return normalized.length() > limit ? normalized.substring(0, limit) : normalized;
    }

    private ReviewResult normalizeImplementationReview(ReviewResult raw, String designArtifact) {
        if (raw.decision() == ReviewDecision.APPROVED) {
            return raw;
        }

        String combined = String.join("\n",
                nullToEmpty(raw.summary()),
                nullToEmpty(raw.changeRequest()),
                nullToEmpty(raw.evidence()),
                nullToEmpty(raw.actionItems())
        );
        boolean hasPerformanceClaim = PERFORMANCE_CLAIM_PATTERN.matcher(combined).find();
        boolean hasMeasurementEvidence = MEASUREMENT_EVIDENCE_PATTERN.matcher(nullToEmpty(raw.evidence())).find();
        boolean designRequiresPerformanceMeasurement = designRequiresPerformanceMeasurement(designArtifact);

        if (hasPerformanceClaim && !hasMeasurementEvidence) {
            if (designRequiresPerformanceMeasurement) {
                return new ReviewResult(
                        ReviewDecision.REVISION_REQUIRED,
                        FixMode.PATCH,
                        "技术方案已要求性能测量，但当前实现未提供对应实测数据。",
                        "请按 DESIGN 中定义的性能验证策略补充 6x6/9x9 的基础测量结果，再决定是否需要进一步优化。",
                        raw.evidence().isBlank()
                                ? "当前 review 未提供来自自检或测试的耗时测量数据；而 DESIGN 已包含性能/耗时验证要求。"
                                : raw.evidence(),
                        """
                        1. 按技术方案中的指标记录 6x6 和 9x9 的实际耗时。
                        2. 补充关键路径（如生成、唯一解校验、模式切换）的基础测量。
                        3. 将测量结果写入实现报告，再判断是否需要性能优化。
                        """.replace("\n", " ").trim()
                );
            }
            return new ReviewResult(
                    ReviewDecision.APPROVED,
                    FixMode.NONE,
                    "当前实现存在性能风险，但缺少明确的性能测量证据；该项下放到 TEST 阶段验证。",
                    "",
                    raw.evidence().isBlank()
                            ? "当前 review 未提供来自自检或测试的耗时测量数据，无法支持“性能未达标”的确定性结论。"
                            : raw.evidence(),
                    """
                    1. 在生成入口记录 6x6 和 9x9 的实际耗时。
                    2. 单独测量唯一解校验和回溯关键路径耗时。
                    3. 在 TEST 阶段基于实测数据判断是否需要性能优化。
                    """.replace("\n", " ").trim()
            );
        }

        if (!raw.evidence().isBlank() && !raw.actionItems().isBlank()) {
            return raw;
        }

        return new ReviewResult(
                raw.decision(),
                raw.fixMode(),
                raw.summary(),
                raw.changeRequest(),
                raw.evidence().isBlank()
                        ? "请结合实际代码变更、自检或测试结果补充支持该结论的直接证据。"
                        : raw.evidence(),
                raw.actionItems().isBlank()
                        ? "1. 根据 changeRequest 定位受影响文件和函数。 2. 先修复最小闭环问题，再重新执行自检和验证。"
                        : raw.actionItems()
        );
    }

    private ReviewResult normalizeDocumentReview(StageType stageType, ReviewResult raw) {
        if (raw.decision() == ReviewDecision.APPROVED) {
            return raw;
        }

        String combined = String.join("\n",
                nullToEmpty(raw.summary()),
                nullToEmpty(raw.changeRequest()),
                nullToEmpty(raw.evidence()),
                nullToEmpty(raw.actionItems())
        );

        if (stageType == StageType.ANALYSIS) {
            boolean asksForDownstreamDetail = ANALYSIS_DOWNSTREAM_DETAIL_PATTERN.matcher(combined).find();
            boolean mentionsCoreAnalysisGap = ANALYSIS_CORE_GAP_PATTERN.matcher(combined).find();
            if (!asksForDownstreamDetail || mentionsCoreAnalysisGap) {
                return raw;
            }

            return new ReviewResult(
                    ReviewDecision.APPROVED,
                    FixMode.NONE,
                    "需求分析已满足当前阶段要求；更细的算法、性能验证和原型细节下放到 DESIGN/TEST_CASE 阶段。",
                    "",
                    raw.evidence(),
                    raw.actionItems()
            );
        }

        if (stageType == StageType.PRD) {
            boolean asksForDownstreamDetail = PRD_DOWNSTREAM_DETAIL_PATTERN.matcher(combined).find();
            boolean mentionsCorePrdGap = PRD_CORE_GAP_PATTERN.matcher(combined).find();
            if (!asksForDownstreamDetail || mentionsCorePrdGap) {
                return raw;
            }

            return new ReviewResult(
                    ReviewDecision.APPROVED,
                    FixMode.NONE,
                    "PRD 已满足当前阶段要求；算法、模块和性能测试细节下放到 DESIGN/TEST_CASE 阶段。",
                    "",
                    raw.evidence(),
                    raw.actionItems()
            );
        }

        return raw;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String readStageArtifact(RunRecord runRecord, StageType stageType) {
        StageExecution stageExecution = runRecord.stageStates().get(stageType);
        if (stageExecution == null || stageExecution.artifactPath() == null) {
            return "";
        }
        try {
            return Files.readString(Path.of(stageExecution.artifactPath()));
        } catch (IOException ignored) {
            return "";
        }
    }

    private boolean designRequiresPerformanceMeasurement(String designArtifact) {
        if (designArtifact == null || designArtifact.isBlank()) {
            return false;
        }
        return DESIGN_PERFORMANCE_REQUIREMENT_PATTERN.matcher(designArtifact).find();
    }

    private ReviewResult enforceDocumentStructure(StageType stageType, String candidateContent, String artifactLabel, ReviewResult baseResult) {
        List<String> requiredSections = requiredSections(stageType);
        List<String> invalidSections = requiredSections.stream()
                .filter(section -> sectionMissingOrEmpty(candidateContent, section, requiredSections))
                .toList();
        if (!invalidSections.isEmpty()) {
            String missing = invalidSections.stream().collect(Collectors.joining("、"));
            return new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                    artifactLabel + " 缺少规范章节：" + trimFinding(missing, 80),
                    "请补齐以下章节后重试：" + missing
            );
        }

        String integrityIssue = detectDocumentIntegrityIssue(stageType, candidateContent, requiredSections);
        if (integrityIssue == null) {
            return baseResult;
        }
        return new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                artifactLabel + " 存在结构完整性问题：" + integrityIssue,
                "请修复文档结构问题后重试：" + integrityIssue
        );
    }

    private String detectDocumentIntegrityIssue(StageType stageType, String candidateContent, List<String> orderedSections) {
        List<String> malformedHeadings = candidateContent.lines()
                .map(String::trim)
                .filter(line -> SECOND_LEVEL_HEADING_PATTERN.matcher(line).matches())
                .filter(line -> !VALID_SECOND_LEVEL_HEADING_FORMAT_PATTERN.matcher(line).matches()
                        || TRUNCATED_TAIL_PATTERN.matcher(line.replaceFirst("^##\\s+", "")).matches())
                .toList();
        if (!malformedHeadings.isEmpty()) {
            return "存在非法或污染的章节标题：" + malformedHeadings.getFirst();
        }

        String trimmed = candidateContent.trim();
        if (trimmed.isBlank()) {
            return "文档为空。";
        }

        String lastMeaningfulLine = trimmed.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .reduce((first, second) -> second)
                .orElse("");
        if (TRUNCATED_TAIL_PATTERN.matcher(lastMeaningfulLine).matches()) {
            return "文档末尾疑似被截断：" + trimFinding(lastMeaningfulLine, 60);
        }

        if (stageType == StageType.PRD) {
            for (String section : orderedSections) {
                String block = extractSectionBlock(candidateContent, section, orderedSections);
                if (block == null) {
                    continue;
                }
                String withoutHeading = block.replaceFirst("(?s)^" + Pattern.quote(section) + "\\s*", "").trim();
                if (!withoutHeading.isBlank()) {
                    String tailLine = withoutHeading.lines()
                            .map(String::trim)
                            .filter(line -> !line.isBlank())
                            .reduce((first, second) -> second)
                            .orElse("");
                    if (TRUNCATED_TAIL_PATTERN.matcher(tailLine).matches()) {
                        return "章节内容疑似被截断：" + section;
                    }
                }
            }
        }
        return null;
    }

    private boolean sectionMissingOrEmpty(String candidateContent, String section, List<String> orderedSections) {
        String block = extractSectionBlock(candidateContent, section, orderedSections);
        if (block == null) {
            return true;
        }
        String withoutHeading = block.replaceFirst("(?s)^" + Pattern.quote(section) + "\\s*", "").trim();
        if (withoutHeading.isBlank()) {
            return true;
        }
        String meaningfulLines = withoutHeading.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.matches("^#{1,6}\\s+.+$"))
                .collect(Collectors.joining("\n"));
        return meaningfulLines.isBlank();
    }

    private String extractSectionBlock(String content, String heading, List<String> orderedSections) {
        int start = content.indexOf(heading);
        if (start < 0) {
            return null;
        }
        int end = content.length();
        int currentIndex = orderedSections.indexOf(heading);
        for (int i = currentIndex + 1; i < orderedSections.size(); i++) {
            int candidate = content.indexOf(orderedSections.get(i), start + heading.length());
            if (candidate >= 0) {
                end = candidate;
                break;
            }
        }
        return content.substring(start, end).trim();
    }

    private List<String> requiredSections(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> List.of(
                    "## 1. 背景与问题定义",
                    "## 2. 目标与成功标准",
                    "## 3. 关键约束",
                    "## 4. 初步调研与假设",
                    "## 5. 边界与非目标",
                    "## 6. 风险与待确认问题"
            );
            case PRD -> List.of(
                    "## 1. 产品目标",
                    "## 2. 目标用户与使用场景",
                    "## 3. 功能范围",
                    "## 4. 非功能要求",
                    "## 5. 验收标准",
                    "## 6. 不做什么"
            );
            case DESIGN -> List.of(
                    "## 1. 技术目标",
                    "## 2. 系统边界与模块划分",
                    "## 3. 核心数据模型",
                    "## 4. 关键流程",
                    "## 5. 接口、页面或命令设计",
                    "## 6. 测试与验证策略",
                    "## 7. 风险与取舍"
            );
            default -> List.of();
        };
    }
}
