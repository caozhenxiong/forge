package devflow.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.editing.CodePreciseEditor;
import devflow.agent.editing.CodePrecisePatch;
import devflow.agent.editing.HtmlPreciseEditor;
import devflow.agent.editing.HtmlPrecisePatch;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import devflow.agent.parsing.TreeSitterParseSummary;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.project.WriteTransaction;
import devflow.agent.review.FixMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ImplementationExecutor {

    private static final int MAX_SUBTASK_ATTEMPTS = 3;
    private static final int MAX_PLAN_PARSE_ATTEMPTS = 3;
    private static final int MAX_FILE_GENERATION_ATTEMPTS = 3;
    private static final int MAX_FILES_PER_SUBTASK = 2;
    private static final int MAX_DELIVERY_POLICY_FILES = 3;
    private static final String FIX_MODE_PATCH_TAG = "[FIX_MODE=PATCH]";
    private static final String FIX_MODE_REWORK_TAG = "[FIX_MODE=REWORK]";
    private static final String REPAIR_BRIEF_TAG = "[REPAIR_BRIEF]";
    private static final String REPAIR_BRIEF_ENFORCED_TAG = "[REPAIR_BRIEF_ENFORCED]";
    private static final String DELIVERY_MODE_TAG = "[DELIVERY_MODE=";
    private static final String DELIVERY_MAX_FILES_TAG = "[DELIVERY_MAX_FILES=";
    private static final String DELIVERY_MAX_SYMBOLS_TAG = "[DELIVERY_MAX_SYMBOLS=";
    private static final String DELIVERY_PREFER_PRECISE_TAG = "[DELIVERY_PREFER_PRECISE_EDITING=";
    private static final String DELIVERY_FORCE_BACKLOG_SPLIT_TAG = "[DELIVERY_FORCE_BACKLOG_SPLIT=";
    private static final String DELIVERY_REQUIRE_VERIFICATION_TAG = "[DELIVERY_REQUIRE_VERIFICATION=";
    private static final Pattern DESIGN_PERFORMANCE_REQUIREMENT_PATTERN = Pattern.compile(
            "(\\d+\\s*(ms|毫秒|s|秒)|p95|benchmark|基准|基准测试|性能验收|性能要求|记录\\s*.*耗时|测量\\s*.*耗时|生成时间\\s*(小于|低于|<)|响应时间\\s*(小于|低于|<)|切换时间\\s*(小于|低于|<))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FRONTEND_GOAL_PATTERN = Pattern.compile(
            "(网页|页面|前端|html|css|javascript|浏览器|游戏|canvas|web|ui)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INLINE_SCRIPT_PATTERN = Pattern.compile(
            "<script(?![^>]*src=)[^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final LlmProvider llmProvider;
    private final FileProjectWorkspace workspace;
    private final ObjectMapper objectMapper;
    private final TestExecutor testExecutor;
    private final TreeSitterSupport treeSitterSupport;
    private final HtmlPreciseEditor htmlPreciseEditor;
    private final CodePreciseEditor codePreciseEditor;

    public ImplementationExecutor(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TestExecutor testExecutor
    ) {
        this(llmProvider, workspace, objectMapper, testExecutor, new TreeSitterSupport());
    }

    @Autowired
    public ImplementationExecutor(
            LlmProvider llmProvider,
            FileProjectWorkspace workspace,
            ObjectMapper objectMapper,
            TestExecutor testExecutor,
            TreeSitterSupport treeSitterSupport
    ) {
        this.llmProvider = llmProvider;
        this.workspace = workspace;
        this.objectMapper = objectMapper;
        this.testExecutor = testExecutor;
        this.treeSitterSupport = treeSitterSupport;
        this.htmlPreciseEditor = new HtmlPreciseEditor(treeSitterSupport);
        this.codePreciseEditor = new CodePreciseEditor(treeSitterSupport);
    }

    public ImplementationExecutionBundle execute(Path projectPath, RunRecord runRecord, String analysis, String prd, String design, String note) {
        String workspaceContext = workspace.collectContext(projectPath, 24, 12000, 60000);
        String performanceValidationGuidance = extractPerformanceValidationGuidance(design);
        DeliveryPolicyEnvelope deliveryPolicy = parseDeliveryPolicy(note);
        boolean preferSkeletonFlow = deliveryPolicy.mode() == DeliveryMode.SKELETON
                || (deliveryPolicy.forceBacklogSplit() && deliveryPolicy.mode() != DeliveryMode.PATCH)
                || shouldPreferSkeletonFlow(runRecord.goal(), runRecord.constraints(), workspaceContext);
        ImplementationPlan plan = planImplementation(
                runRecord,
                analysis,
                prd,
                design,
                note,
                workspaceContext,
                performanceValidationGuidance,
                preferSkeletonFlow,
                deliveryPolicy
        );
        // Large tasks are more stable when we decompose them into explicit implementation
        // subtasks, validate each subtask, and only then move on to the next one.
        List<SubtaskExecutionReport> reports = executePlan(projectPath, runRecord, analysis, prd, design, note, plan);
        return new ImplementationExecutionBundle(
                renderReport(plan, reports, note),
                renderBacklog(plan, deliveryPolicy),
                renderRepairAlignment(plan, reports, note, deliveryPolicy)
        );
    }

    private ImplementationPlan planImplementation(
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            String workspaceContext,
            String performanceValidationGuidance,
            boolean preferSkeletonFlow,
            DeliveryPolicyEnvelope deliveryPolicy
    ) {
        FixMode fixMode = extractFixMode(note);
        String system = """
                你是一个资深软件工程师。你需要把一次大的实现任务拆成可落地、可验证的子步骤。
                你必须只返回一个 JSON 对象，不要输出任何额外解释。
                JSON 格式：
                {
                  "summary": "本次实现总体摘要",
                  "subtasks": [
                    {
                      "title": "子任务标题",
                      "goal": "该子任务要完成什么",
                      "deliveryMode": "SKELETON|INCREMENTAL|PATCH|REWORK",
                      "acceptanceCriteria": ["验收标准1", "验收标准2"],
                      "changes": [
                        {
                          "path": "相对路径",
                          "action": "WRITE|DELETE",
                          "reason": "为什么要改这个文件"
                        }
                      ]
                    }
                  ]
                }

                约束：
                1. 子任务数量控制在 3 到 6 个
                2. 每个子任务都必须可单独验证
                3. 每个子任务最多改 2 个文件
                4. 优先最小改动
                5. 只列出真正需要改动的文件
                6. 不要在此步骤输出文件内容
                7. 保持项目可编译、可测试
                8. deliveryMode 必须明确选择
                9. 若任务较大，优先拆成“骨架 -> 功能填充 -> 交互补全 -> polish/验证”
                """;
        system = system + """

                本轮交付策略：
                1. deliveryMode 参考建议：%s
                2. 每个子任务最多改 %d 个文件
                3. 每个子任务最多变更 %d 个符号
                4. preferPreciseEditing=%s
                5. forceBacklogSplit=%s
                6. requireVerificationBeforeReview=%s
                """.formatted(
                deliveryPolicy.mode(),
                deliveryPolicy.maxFiles(),
                deliveryPolicy.maxSymbols(),
                deliveryPolicy.preferPreciseEditing(),
                deliveryPolicy.forceBacklogSplit(),
                deliveryPolicy.requireVerificationBeforeReview()
        );
        if (fixMode == FixMode.PATCH) {
            system = system + """

                    这是修复模式：
                    1. 只围绕当前反馈做最小补丁修改
                    2. 优先复用现有文件和现有模块，不要新增重复模块
                    3. 不要推翻已经完成的功能
                    4. 子任务数量尽量控制在 1 到 3 个
                    """;
        } else if (fixMode == FixMode.REWORK) {
            system = system + """

                    这是重构模式：
                    1. 可以调整文件结构和模块划分来解决结构性问题
                    2. 优先消除重复实现、未接线文件和错误分层
                    3. 仍然要围绕当前代码和需求收敛，不要无意义推翻重来
                    4. 子任务数量控制在 3 到 6 个
                    """;
        }
        if (note != null && note.contains(REPAIR_BRIEF_TAG)) {
            system = system + """

                    这是 repair brief 驱动的修复：
                    1. 优先围绕 diagnosis 输出的根因和证据修复
                    2. 子任务要直接对准 Must Fix First、Acceptance Target 和 Acceptance Checks
                    3. 不要重新发散成新的大范围实现目标
                    4. 不要沿着 Forbidden Directions 继续重复失败路径
                    """;
        }
        if (preferSkeletonFlow) {
            system = system + """

                    当前任务更适合小步交付：
                    1. 第一子任务优先建立最小可运行骨架，deliveryMode 使用 SKELETON
                    2. 优先按结构搭建、核心逻辑、接线与验证逐步拆分，避免单文件塞入全部 HTML/CSS/JS
                    3. 后续子任务使用 INCREMENTAL，逐步填充核心逻辑、输入控制、状态更新和 polish
                    4. 不要试图在一个子任务里完成整个页面或整个游戏
                    5. 每个子任务完成后，项目应保持“至少可打开、可自检”
                    """;
        }
        if (!performanceValidationGuidance.isBlank()) {
            system = system + """

                    DESIGN 已定义基础性能/验证要求：
                    1. 若技术方案要求记录耗时或 benchmark，请把相应测量入口纳入实现子任务
                    2. 若需要在 TEST 阶段自动验收，优先提供可读取的页面测量信号，例如 window.__devflowMetrics
                    3. 优先补充轻量、可执行的基础测量，不要无意义扩展复杂压测框架
                    4. 若设计没有要求性能测量，不要自行发散额外 benchmark
                    """;
        }
        String user = """
                目标：
                %s

                约束：
                %s

                需求分析：
                %s

                产品需求文档：
                %s

                技术方案设计：
                %s

                当前备注：
                %s

                当前工作区上下文：
                %s

                本轮必需证据：
                %s

                %s
                """.formatted(
                runRecord.goal(),
                runRecord.constraints(),
                analysis,
                prd,
                design,
                note,
                workspaceContext,
                renderBulletList(deliveryPolicy.requiredEvidence()),
                performanceValidationGuidance
        );
        String response = llmProvider.generate(system, user, Map.of("num_predict", 2600), ModelRole.IMPLEMENTATION);
        return parsePlanWithRepair(response, fixMode, preferSkeletonFlow, deliveryPolicy);
    }

    private List<SubtaskExecutionReport> executePlan(
            Path projectPath,
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            ImplementationPlan plan
    ) {
        List<SubtaskExecutionReport> reports = new ArrayList<>();
        String persistentRepairFeedback = repairFeedback(note);
        String sharedFeedback = mergeFeedback(persistentRepairFeedback, note == null ? "" : note);
        for (Subtask subtask : plan.subtasks()) {
            SubtaskExecutionReport report = executeSubtask(
                    projectPath,
                    runRecord,
                    analysis,
                    prd,
                    design,
                    plan.summary(),
                    subtask,
                    sharedFeedback,
                    persistentRepairFeedback
            );
            reports.add(report);
            if (!report.completed()) {
                break;
            }
            sharedFeedback = mergeFeedback(persistentRepairFeedback, report.lastVerifierChangeRequest());
        }
        return reports;
    }

    private SubtaskExecutionReport executeSubtask(
            Path projectPath,
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String planSummary,
            Subtask subtask,
            String inheritedFeedback,
            String persistentRepairFeedback
    ) {
        List<SubtaskAttemptReport> attempts = new ArrayList<>();
        String feedback = inheritedFeedback == null ? "" : inheritedFeedback;
        for (int attempt = 1; attempt <= MAX_SUBTASK_ATTEMPTS; attempt++) {
            applySubtask(projectPath, analysis, prd, design, planSummary, subtask, feedback);
            SelfCheckResult selfCheck = testExecutor.selfCheck(projectPath);
            ReviewResult verification = verifySubtask(projectPath, runRecord, subtask, selfCheck, feedback);
            attempts.add(new SubtaskAttemptReport(attempt, selfCheck, verification));
            if (selfCheck.passed() && verification.decision() == ReviewDecision.APPROVED) {
                return new SubtaskExecutionReport(subtask, true, attempts);
            }
            feedback = mergeFeedback(persistentRepairFeedback, buildRetryFeedback(selfCheck, verification));
        }
        return new SubtaskExecutionReport(subtask, false, attempts);
    }

    private void applySubtask(
            Path projectPath,
            String analysis,
            String prd,
            String design,
            String planSummary,
            Subtask subtask,
            String feedback
    ) {
        for (FileChange change : subtask.changes()) {
            Path relativePath = Path.of(change.path()).normalize();
            switch (change.action()) {
                case WRITE -> writeFileTransactionally(
                        projectPath,
                        relativePath,
                        generateFileContent(projectPath, relativePath, analysis, prd, design, planSummary, subtask, feedback, change.reason())
                );
                case DELETE -> workspace.deleteFile(projectPath, relativePath);
            }
        }
    }

    private void writeFileTransactionally(Path projectPath, Path relativePath, String content) {
        WriteTransaction transaction = workspace.stageWrite(projectPath, relativePath, content);
        String stagedContent = workspace.readStagedContent(transaction);
        String validationFailure = validateGeneratedContent(projectPath, relativePath, stagedContent);
        if (validationFailure != null) {
            workspace.failWrite(transaction, validationFailure);
            throw new IllegalStateException("Generated file content is incomplete or invalid for " + relativePath + ": " + validationFailure);
        }
        try {
            workspace.commitWrite(transaction);
        } catch (RuntimeException exception) {
            workspace.failWrite(transaction, "Commit failure: " + exception.getMessage());
            throw exception;
        }
    }

    private String renderReport(ImplementationPlan plan, List<SubtaskExecutionReport> reports, String note) {
        StringBuilder builder = new StringBuilder("""
                # 代码实现

                ## 实现摘要

                %s

                ## 子任务拆解

                """.formatted(plan.summary()));
        if (plan.subtasks().isEmpty()) {
            builder.append("- 无子任务\n");
        } else {
            for (int index = 0; index < plan.subtasks().size(); index++) {
                Subtask subtask = plan.subtasks().get(index);
                builder.append("### ").append(index + 1).append(". ").append(subtask.title()).append("\n\n");
                builder.append("- 目标：").append(subtask.goal()).append('\n');
                builder.append("- 交付模式：").append(subtask.deliveryMode()).append('\n');
                builder.append("- 验收标准：").append(String.join("；", safeList(subtask.acceptanceCriteria()))).append('\n');
                builder.append("- 涉及文件：").append(renderChangeList(subtask.changes())).append("\n\n");
            }
        }

        String repairAlignmentSection = renderRepairAlignmentSection(note);
        if (!repairAlignmentSection.isBlank()) {
            builder.append(repairAlignmentSection).append("\n\n");
        }

        builder.append("## 子任务执行结果\n\n");
        if (reports.isEmpty()) {
            builder.append("- 未执行任何子任务\n");
            return builder.toString();
        }

        for (SubtaskExecutionReport report : reports) {
            builder.append("### ").append(report.subtask().title()).append("\n\n");
            builder.append("- 最终状态：").append(report.completed() ? "COMPLETED" : "FAILED").append('\n');
            for (SubtaskAttemptReport attempt : report.attempts()) {
                builder.append("- attempt=").append(attempt.attempt())
                        .append(" selfCheck=").append(attempt.selfCheck().passed() ? "PASS" : "FAIL")
                        .append(" verifier=").append(attempt.review().decision())
                        .append(" summary=").append(attempt.review().summary())
                        .append('\n');
                if (!attempt.selfCheck().summary().isBlank()) {
                    builder.append("  - selfCheckSummary: ").append(attempt.selfCheck().summary()).append('\n');
                }
                if (!attempt.review().changeRequest().isBlank()) {
                    builder.append("  - verifierChangeRequest: ").append(attempt.review().changeRequest()).append('\n');
                }
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private String renderBacklog(ImplementationPlan plan, DeliveryPolicyEnvelope deliveryPolicy) {
        StringBuilder builder = new StringBuilder("""
                # Implementation Backlog

                - recommendedMode: %s
                - maxFiles: %d
                - maxSymbols: %d
                - preferPreciseEditing: %s
                - forceBacklogSplit: %s
                - requireVerificationBeforeReview: %s

                ## Backlog Items

                """.formatted(
                deliveryPolicy.mode(),
                deliveryPolicy.maxFiles(),
                deliveryPolicy.maxSymbols(),
                deliveryPolicy.preferPreciseEditing(),
                deliveryPolicy.forceBacklogSplit(),
                deliveryPolicy.requireVerificationBeforeReview()
        ));
        int index = 1;
        for (Subtask subtask : plan.subtasks()) {
            builder.append("### ").append(index++).append(". ").append(subtask.title()).append("\n\n");
            builder.append("- goal: ").append(subtask.goal()).append("\n");
            builder.append("- deliveryMode: ").append(subtask.deliveryMode()).append("\n");
            builder.append("- files: ").append(renderChangeList(subtask.changes())).append("\n");
            builder.append("- acceptance: ").append(String.join("；", safeList(subtask.acceptanceCriteria()))).append("\n\n");
        }
        return builder.toString().trim();
    }

    private String renderRepairAlignment(
            ImplementationPlan plan,
            List<SubtaskExecutionReport> reports,
            String note,
            DeliveryPolicyEnvelope deliveryPolicy
    ) {
        if (note == null || !note.contains(REPAIR_BRIEF_ENFORCED_TAG)) {
            return """
                    # Repair Alignment

                    - status: NOT_APPLICABLE
                    - note: 当前实现不处于 repair brief 强约束模式。
                    """;
        }
        List<String> completedSubtasks = reports.stream()
                .filter(SubtaskExecutionReport::completed)
                .map(report -> report.subtask().title())
                .toList();
        String noteSummary = summarizeForVerification(note, 2600);
        return """
                # Repair Alignment

                - status: ACTIVE
                - deliveryMode: %s
                - completedSubtasks: %s

                ## Repair Context

                ```text
                %s
                ```

                ## Covered Backlog Items

                %s
                """.formatted(
                deliveryPolicy.mode(),
                completedSubtasks.isEmpty() ? "(none)" : String.join("，", completedSubtasks),
                noteSummary,
                renderBulletList(completedSubtasks)
        );
    }

    private ImplementationPlan parsePlanWithRepair(
            String response,
            FixMode fixMode,
            boolean preferSkeletonFlow,
            DeliveryPolicyEnvelope deliveryPolicy
    ) {
        String candidate = response;
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_PLAN_PARSE_ATTEMPTS; attempt++) {
            try {
                return parsePlan(candidate, fixMode, preferSkeletonFlow, deliveryPolicy);
            } catch (Exception exception) {
                lastException = exception;
                if (attempt == MAX_PLAN_PARSE_ATTEMPTS) {
                    break;
                }
                candidate = repairPlan(candidate, exception);
            }
        }
        throw new IllegalStateException("Failed to parse implementation plan: " + response, lastException);
    }

    private ImplementationPlan parsePlan(
            String response,
            FixMode fixMode,
            boolean preferSkeletonFlow,
            DeliveryPolicyEnvelope deliveryPolicy
    ) throws Exception {
        ImplementationPlan plan = objectMapper.readValue(extractJsonObject(response), ImplementationPlan.class);
        if (plan.subtasks() == null || plan.subtasks().isEmpty()) {
            throw new IllegalStateException("Implementation plan must contain subtasks");
        }
        List<Subtask> normalized = new ArrayList<>();
        for (int index = 0; index < plan.subtasks().size(); index++) {
            Subtask subtask = plan.subtasks().get(index);
            if (subtask.changes() == null || subtask.changes().isEmpty()) {
                throw new IllegalStateException("Each subtask must contain at least one file change");
            }
            if (subtask.changes().size() > deliveryPolicy.maxFiles()) {
                throw new IllegalStateException("Each subtask may change at most %d files".formatted(deliveryPolicy.maxFiles()));
            }
            DeliveryMode deliveryMode = resolveDeliveryMode(
                    subtask.deliveryMode(),
                    fixMode,
                    preferSkeletonFlow,
                    index,
                    subtask.changes(),
                    deliveryPolicy
            );
            normalized.add(new Subtask(
                    subtask.title(),
                    subtask.goal(),
                    subtask.acceptanceCriteria(),
                    deliveryMode,
                    subtask.changes()
            ));
        }
        return new ImplementationPlan(plan.summary(), normalized);
    }

    private String repairPlan(String brokenResponse, Exception exception) {
        String system = """
                你是 JSON 修复器。请修复输入中的 implementation plan，使其成为合法 JSON。
                你必须只返回修复后的 JSON 对象，不要输出任何额外解释。
                保持原有字段语义不变，字段格式必须是：
                {
                  "summary": "字符串",
                  "subtasks": [
                    {
                      "title": "字符串",
                      "goal": "字符串",
                      "deliveryMode": "SKELETON|INCREMENTAL|PATCH|REWORK",
                      "acceptanceCriteria": ["字符串"],
                      "changes": [
                        {
                          "path": "相对路径",
                          "action": "WRITE|DELETE",
                          "reason": "字符串"
                        }
                      ]
                    }
                  ]
                }
                """;
        String user = """
                当前 JSON 解析错误：
                %s

                待修复内容：
                %s
                """.formatted(exception.getMessage(), brokenResponse);
        return llmProvider.generate(system, user, Map.of("num_predict", 2600), ModelRole.REPAIR);
    }

    private String extractJsonObject(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("No JSON object found in model response");
        }
        return response.substring(start, end + 1);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String generateFileContent(
            Path projectPath,
            Path relativePath,
            String analysis,
            String prd,
            String design,
            String planSummary,
            Subtask subtask,
            String feedback,
            String reason
    ) {
        String existingContent = Files.exists(projectPath.resolve(relativePath))
                ? workspace.readFile(projectPath, relativePath)
                : "(new file)";
        if (shouldUsePreciseHtmlEditing(projectPath, relativePath, subtask.deliveryMode(), existingContent)) {
            return generatePreciseHtmlContent(
                    projectPath,
                    relativePath,
                    analysis,
                    prd,
                    design,
                    planSummary,
                    subtask,
                    feedback,
                    reason,
                    existingContent
            );
        }
        if (shouldUsePreciseCodeEditing(projectPath, relativePath, subtask.deliveryMode(), existingContent)) {
            return generatePreciseCodeContent(
                    projectPath,
                    relativePath,
                    analysis,
                    prd,
                    design,
                    planSummary,
                    subtask,
                    feedback,
                    reason,
                    existingContent
            );
        }
        String targetedContext = renderTargetedContext(projectPath, subtask.changes(), relativePath);
        String system = """
                你是资深软件工程师。请只输出目标文件的完整最终内容。
                不要解释，不要 markdown 代码块，不要补充额外文字。
                """;
        DeliveryMode deliveryMode = subtask.deliveryMode();
        FixMode fixMode = extractFixMode(feedback);
        if (deliveryMode == DeliveryMode.SKELETON) {
            system = system + """

                    当前处于骨架模式：
                    1. 只建立最小可运行骨架，不要一次塞入全部复杂逻辑
                    2. 页面/程序必须可打开、可自检、结构合法
                    3. 前端项目优先输出清晰的 HTML 结构、基础样式入口、最小脚本入口和占位函数
                    4. 可以为后续子任务预留明确的函数、模块、DOM 容器或注释占位
                    5. 不要为了追求完整而让单文件过长
                    """;
        } else if (deliveryMode == DeliveryMode.INCREMENTAL) {
            system = system + """

                    当前处于渐进填充模式：
                    1. 只补当前子任务的功能，不要扩展到无关能力
                    2. 优先在已有骨架上增量添加函数、状态和事件，不要重写整套结构
                    3. 保持已有入口、样式、模块边界不变
                    4. 单次改动要尽量小，避免大段推翻式改写
                    """;
        }
        if (pathLooksLikeHtml(relativePath)) {
            system = system + """

                    HTML 入口文件约束：
                    1. 请优先输出可持续增量修改的结构
                    2. 主内容容器使用 <main id="app-root">...</main>
                    3. 内联样式使用 <style id="app-style">...</style>
                    4. 主脚本使用 <script id="app-script">...</script>
                    5. 后续精确改写会依赖这些稳定锚点，请保持这些 id 不变
                    """;
        }
        if (fixMode == FixMode.PATCH) {
            system = system + """

                    当前处于修复模式：
                    1. 只修复反馈中明确指出的问题
                    2. 尽量保留既有代码结构和已有功能
                    3. 不要为了修一个点而重写整份文件
                    """;
        } else if (fixMode == FixMode.REWORK) {
            system = system + """

                    当前处于重构模式：
                    1. 允许较大范围调整结构来解决根本性问题
                    2. 优先解决重复模块、入口未接线、模块边界混乱
                    3. 重构后必须保持入口文件、模块引用和测试链路一致
                    """;
        }
        if (feedback != null && feedback.contains(REPAIR_BRIEF_TAG)) {
            system = system + """

                    当前输入包含 repair brief：
                    1. 优先修复 diagnosis 明确指出的根因
                    2. 不要偏离 repair brief 中的 affectedFiles、doNotChange 和 acceptanceTarget
                    3. 如果没有必要，不要新增无关文件或改动无关模块
                    """;
        }
        String user = """
                总体实现摘要：
                %s

                当前子任务：
                - 标题：%s
                - 目标：%s
                - 交付模式：%s
                - 验收标准：%s

                文件路径：
                %s

                变更原因：
                %s

                需求分析：
                %s

                产品需求文档：
                %s

                技术方案设计：
                %s

                上一轮反馈：
                %s

                当前相关文件上下文：
                %s

                当前内容：
                %s

                请输出该文件修改后的完整内容。
                """.formatted(
                planSummary,
                subtask.title(),
                subtask.goal(),
                subtask.deliveryMode(),
                String.join("；", safeList(subtask.acceptanceCriteria())),
                relativePath,
                reason,
                analysis,
                prd,
                design,
                nullToEmpty(feedback),
                targetedContext,
                existingContent
        );
        String retryFeedback = "";
        for (int attempt = 1; attempt <= MAX_FILE_GENERATION_ATTEMPTS; attempt++) {
            String prompt = retryFeedback.isBlank() ? user : user + "\n\n上一轮输出不可接受，请修正后重新生成：\n" + retryFeedback;
            String generated = stripCodeFence(
                    llmProvider.generate(system, prompt, Map.of("num_predict", numPredictFor(subtask.deliveryMode())), ModelRole.IMPLEMENTATION)
            );
            String validationFailure = validateGeneratedContent(projectPath, relativePath, generated);
            if (validationFailure == null) {
                return generated;
            }
            retryFeedback = """
                    - attempt: %d
                    - 文件: %s
                    - 问题: %s
                    要求：
                    1. 重新输出完整文件
                    2. 不要省略结尾
                    3. 保证结构闭合、脚本可解析
                    """.formatted(attempt, relativePath, validationFailure);
        }
        throw new IllegalStateException("Generated file content is incomplete or invalid for " + relativePath);
    }

    private String generatePreciseHtmlContent(
            Path projectPath,
            Path relativePath,
            String analysis,
            String prd,
            String design,
            String planSummary,
            Subtask subtask,
            String feedback,
            String reason,
            String existingContent
    ) {
        String targetedContext = renderTargetedContext(projectPath, subtask.changes(), relativePath);
        String system = """
                你是资深前端工程师。请对现有 HTML 页面做“精确改写”，不要整页重写。
                你必须只返回一个 JSON 对象，格式如下：
                {
                  "markupHtml": "main#app-root 的内部 HTML；不修改则返回 null",
                  "styleCss": "style#app-style 的 CSS 内容；不修改则返回 null",
                  "scriptJs": "script#app-script 的 JS 内容；不修改则返回 null"
                }

                规则：
                1. 只返回 JSON，不要解释，不要 markdown
                2. 不要输出完整 HTML 文档
                3. 只修改必要区块，未修改的区块返回 null
                4. markupHtml 只包含 <main id="app-root"> 的内部内容，不要再包一层 <main>
                5. styleCss 只包含纯 CSS，不要包 <style>
                6. scriptJs 只包含纯 JavaScript，不要包 <script>
                7. 当前是精确改写模式，优先最小改动
                """;
        String user = """
                总体实现摘要：
                %s

                当前子任务：
                - 标题：%s
                - 目标：%s
                - 交付模式：%s
                - 验收标准：%s

                文件路径：
                %s

                变更原因：
                %s

                需求分析：
                %s

                产品需求文档：
                %s

                技术方案设计：
                %s

                上一轮反馈：
                %s

                当前精确改写锚点：
                %s

                当前相关文件上下文：
                %s

                当前 HTML 内容：
                %s
                """.formatted(
                planSummary,
                subtask.title(),
                subtask.goal(),
                subtask.deliveryMode(),
                String.join("；", safeList(subtask.acceptanceCriteria())),
                relativePath,
                reason,
                analysis,
                prd,
                design,
                nullToEmpty(feedback),
                htmlPreciseEditor.describeAnchors(existingContent),
                targetedContext,
                summarizeForVerification(existingContent, 12000)
        );
        String retryFeedback = "";
        for (int attempt = 1; attempt <= MAX_FILE_GENERATION_ATTEMPTS; attempt++) {
            String prompt = retryFeedback.isBlank() ? user : user + "\n\n上一轮精确改写失败，请修正后重新生成：\n" + retryFeedback;
            String generated = stripCodeFence(
                    llmProvider.generate(system, prompt, Map.of("num_predict", 1400), ModelRole.IMPLEMENTATION)
            );
            try {
                HtmlPrecisePatch patch = objectMapper.readValue(extractJsonObject(generated), HtmlPrecisePatch.class);
                String merged = htmlPreciseEditor.applyPatch(existingContent, patch);
                String validationFailure = validateGeneratedContent(projectPath, relativePath, merged);
                if (validationFailure == null) {
                    return merged;
                }
                retryFeedback = """
                        - attempt: %d
                        - 文件: %s
                        - 问题: %s
                        要求：
                        1. 继续使用 JSON 精确改写格式
                        2. 只返回需要修改的区块
                        3. 保证改写后 HTML、脚本和样式都可解析
                        """.formatted(attempt, relativePath, validationFailure);
            } catch (Exception exception) {
                retryFeedback = """
                        - attempt: %d
                        - 文件: %s
                        - 问题: %s
                        要求：
                        1. 必须只返回合法 JSON
                        2. 至少修改一个区块
                        3. 不要输出完整 HTML 文档
                        """.formatted(attempt, relativePath, exception.getMessage());
            }
        }
        throw new IllegalStateException("Generated precise HTML patch is incomplete or invalid for " + relativePath);
    }

    private String generatePreciseCodeContent(
            Path projectPath,
            Path relativePath,
            String analysis,
            String prd,
            String design,
            String planSummary,
            Subtask subtask,
            String feedback,
            String reason,
            String existingContent
    ) {
        String targetedContext = renderTargetedContext(projectPath, subtask.changes(), relativePath);
        String system = """
                你是资深工程师。请对现有源码做“符号级精确改写”，不要整文件重写。
                你必须只返回一个 JSON 对象，格式如下：
                {
                  "operations": [
                    {
                      "action": "REPLACE_SYMBOL|INSERT_INTO_SYMBOL|APPEND_FILE",
                      "targetSymbol": "目标符号名；APPEND_FILE 时可为 null",
                      "targetKind": "class|interface|enum|record|constructor|method|function|type|variable；APPEND_FILE 时可为 null",
                      "content": "要写入的源码片段"
                    }
                  ]
                }

                规则：
                1. 只返回 JSON，不要解释，不要 markdown
                2. 不要输出完整文件内容
                3. REPLACE_SYMBOL 必须提供完整声明
                4. INSERT_INTO_SYMBOL 只在目标符号体内部插入内容
                5. APPEND_FILE 只用于新增顶层符号或补充文件尾部内容
                6. 优先最小改动，优先复用现有符号和结构
                7. 仅选择当前符号清单里真实存在的 targetSymbol/targetKind
                """;
        String user = """
                总体实现摘要：
                %s

                当前子任务：
                - 标题：%s
                - 目标：%s
                - 交付模式：%s
                - 验收标准：%s

                文件路径：
                %s

                变更原因：
                %s

                需求分析：
                %s

                产品需求文档：
                %s

                技术方案设计：
                %s

                上一轮反馈：
                %s

                当前可精确编辑的符号：
                %s

                当前相关文件上下文：
                %s

                当前文件内容：
                %s
                """.formatted(
                planSummary,
                subtask.title(),
                subtask.goal(),
                subtask.deliveryMode(),
                String.join("；", safeList(subtask.acceptanceCriteria())),
                relativePath,
                reason,
                analysis,
                prd,
                design,
                nullToEmpty(feedback),
                codePreciseEditor.describeSymbols(relativePath, existingContent),
                targetedContext,
                summarizeForVerification(existingContent, 12000)
        );
        String retryFeedback = "";
        for (int attempt = 1; attempt <= MAX_FILE_GENERATION_ATTEMPTS; attempt++) {
            String prompt = retryFeedback.isBlank() ? user : user + "\n\n上一轮符号级改写失败，请修正后重新生成：\n" + retryFeedback;
            String generated = stripCodeFence(
                    llmProvider.generate(system, prompt, Map.of("num_predict", 1600), ModelRole.IMPLEMENTATION)
            );
            try {
                CodePrecisePatch patch = objectMapper.readValue(extractJsonObject(generated), CodePrecisePatch.class);
                String merged = codePreciseEditor.applyPatch(relativePath, existingContent, patch);
                String validationFailure = validateGeneratedContent(projectPath, relativePath, merged);
                if (validationFailure == null) {
                    return merged;
                }
                retryFeedback = """
                        - attempt: %d
                        - 文件: %s
                        - 问题: %s
                        要求：
                        1. 继续使用 JSON 符号级改写格式
                        2. 只改必要符号，避免整文件重写
                        3. 产物必须保持语法和结构可解析
                        """.formatted(attempt, relativePath, validationFailure);
            } catch (Exception exception) {
                retryFeedback = """
                        - attempt: %d
                        - 文件: %s
                        - 问题: %s
                        要求：
                        1. 只返回合法 JSON
                        2. operations 至少包含一个有效操作
                        3. 目标符号必须来自给定符号清单
                        """.formatted(attempt, relativePath, exception.getMessage());
            }
        }
        throw new IllegalStateException("Generated precise code patch is incomplete or invalid for " + relativePath);
    }

    private ReviewResult verifySubtask(
            Path projectPath,
            RunRecord runRecord,
            Subtask subtask,
            SelfCheckResult selfCheck,
            String feedback
    ) {
        String candidate = """
                子任务标题：%s

                子任务目标：
                %s

                验收标准：
                %s

                自检结果：
                - passed: %s
                - summary: %s
                - details:
                %s

                当前相关文件：
                %s

                %s

                %s
                """.formatted(
                subtask.title(),
                subtask.goal(),
                String.join("；", safeList(subtask.acceptanceCriteria())),
                selfCheck.passed(),
                selfCheck.summary(),
                selfCheck.details(),
                renderTargetedContext(projectPath, subtask.changes(), null),
                extractPerformanceValidationGuidance(designFromRun(runRecord)),
                renderRepairVerificationContext(feedback)
        );
        return llmProvider.review(
                """
                你是实现阶段的子任务验证器。请只根据子任务目标、验收标准、自检结果和当前文件内容判断该子任务是否已经完成。
                约束：
                1. 只能基于输入里的明确证据下结论，不要猜测运行时结果。
                2. 只有在 DESIGN 明确定义了性能测量要求时，才可以要求补充基础性能测量。
                3. 如果要判定“性能不达标”“耗时超标”“未满足 xx ms”，必须在自检结果或输入文本里存在明确的测量数据。
                4. 如果没有测量数据，只能写“存在性能风险”或“缺少性能验证”，不能直接判定未达标。
                5. 若 DESIGN 未要求性能测量，不要因为缺少性能数据而拒绝当前子任务。
                6. changeRequest 必须可执行，优先指出具体文件、函数、变量或缺失验证。
                7. 如果代码基本正确，只是缺少验证，优先给 PATCH，不要轻易给 REWORK。
                8. 如果输入包含 repair brief，必须优先判断 Must Fix First 和 Acceptance Checks 是否已被覆盖。
                9. 如果实现仍沿着 Forbidden Directions 继续修改，必须拒绝。
                """,
                candidate,
                Map.of("num_predict", 220),
                ModelRole.CODE_REVIEW
        );
    }

    private String designFromRun(RunRecord runRecord) {
        StageExecution execution = runRecord.stageStates().get(StageType.DESIGN);
        if (execution == null || execution.artifactPath() == null) {
            return "";
        }
        try {
            return Files.readString(Path.of(execution.artifactPath()));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractPerformanceValidationGuidance(String design) {
        if (design == null || design.isBlank() || !DESIGN_PERFORMANCE_REQUIREMENT_PATTERN.matcher(design).find()) {
            return "";
        }
        return """
                [DESIGN_PERFORMANCE_VALIDATION]
                技术方案中包含性能/耗时/验证要求，请按该方案补充基础测量或 benchmark 结果，再决定是否需要优化：
                %s
                """.formatted(summarizeForVerification(design, 2400));
    }

    private String buildRetryFeedback(SelfCheckResult selfCheck, ReviewResult verification) {
        return """
                [FIX_MODE=%s]
                上一轮子任务未通过，请只修复下面这些问题：
                - 自检摘要：%s
                - 自检详情：%s
                - 验证结论：%s
                - 验证反馈：%s
                """.formatted(
                verification.fixMode(),
                selfCheck.summary(),
                selfCheck.details(),
                verification.summary(),
                verification.changeRequest()
        );
    }

    private String repairFeedback(String note) {
        if (note == null || !note.contains(REPAIR_BRIEF_TAG)) {
            return "";
        }
        return note.strip();
    }

    private String mergeFeedback(String persistentRepairFeedback, String transientFeedback) {
        String persistent = persistentRepairFeedback == null ? "" : persistentRepairFeedback.strip();
        String transientText = transientFeedback == null ? "" : transientFeedback.strip();
        if (persistent.isBlank()) {
            return transientText;
        }
        if (transientText.isBlank()) {
            return persistent;
        }
        if (transientText.contains(REPAIR_BRIEF_TAG)) {
            return transientText;
        }
        return persistent + "\n\n" + transientText;
    }

    private String renderRepairVerificationContext(String feedback) {
        if (feedback == null || !feedback.contains(REPAIR_BRIEF_TAG)) {
            return "";
        }
        return """
                [REPAIR_BRIEF_VERIFICATION]
                当前子任务处于 repair brief 强约束下，请优先验证：
                1. Must Fix First 是否已有直接证据表明被覆盖
                2. Acceptance Checks 是否至少在当前子任务范围内得到响应
                3. 是否仍沿着 Forbidden Directions 继续偏离

                修复上下文：
                %s
                """.formatted(feedback);
    }

    private String renderRepairAlignmentSection(String note) {
        if (note == null || !note.contains(REPAIR_BRIEF_ENFORCED_TAG)) {
            return "";
        }
        return """
                ## Repair Brief 对齐

                本轮实现处于 repair brief 强约束模式：
                - 必须优先覆盖 Must Fix First
                - 必须避免 Forbidden Directions
                - verifier 将优先检查 Acceptance Checks
                """;
    }

    private boolean shouldPreferSkeletonFlow(String goal, String constraints, String workspaceContext) {
        String combined = (nullToEmpty(goal) + "\n" + nullToEmpty(constraints) + "\n" + nullToEmpty(workspaceContext)).toLowerCase();
        return FRONTEND_GOAL_PATTERN.matcher(combined).find();
    }

    private DeliveryMode resolveDeliveryMode(
            DeliveryMode rawMode,
            FixMode fixMode,
            boolean preferSkeletonFlow,
            int subtaskIndex,
            List<FileChange> changes,
            DeliveryPolicyEnvelope deliveryPolicy
    ) {
        if (deliveryPolicy.mode() != null && deliveryPolicy.mode() != DeliveryMode.INCREMENTAL && rawMode == null) {
            return deliveryPolicy.mode();
        }
        if (rawMode != null) {
            return rawMode;
        }
        if (fixMode == FixMode.PATCH) {
            return DeliveryMode.PATCH;
        }
        if (fixMode == FixMode.REWORK) {
            return DeliveryMode.REWORK;
        }
        if (preferSkeletonFlow && subtaskIndex == 0 && targetsNewEntryOrUiFiles(changes)) {
            return DeliveryMode.SKELETON;
        }
        return DeliveryMode.INCREMENTAL;
    }

    private DeliveryPolicyEnvelope parseDeliveryPolicy(String note) {
        DeliveryMode mode = parseDeliveryModeTag(note, DELIVERY_MODE_TAG);
        Integer maxFiles = parseIntegerTag(note, DELIVERY_MAX_FILES_TAG);
        Integer maxSymbols = parseIntegerTag(note, DELIVERY_MAX_SYMBOLS_TAG);
        Boolean preferPrecise = parseBooleanTag(note, DELIVERY_PREFER_PRECISE_TAG);
        Boolean forceBacklogSplit = parseBooleanTag(note, DELIVERY_FORCE_BACKLOG_SPLIT_TAG);
        Boolean requireVerification = parseBooleanTag(note, DELIVERY_REQUIRE_VERIFICATION_TAG);
        return new DeliveryPolicyEnvelope(
                mode == null ? DeliveryMode.INCREMENTAL : mode,
                maxFiles == null ? MAX_FILES_PER_SUBTASK : Math.max(1, Math.min(maxFiles, MAX_DELIVERY_POLICY_FILES)),
                maxSymbols == null ? 4 : Math.max(1, maxSymbols),
                preferPrecise == null || preferPrecise,
                forceBacklogSplit != null && forceBacklogSplit,
                requireVerification == null || requireVerification,
                extractRequiredEvidence(note)
        );
    }

    private DeliveryMode parseDeliveryModeTag(String note, String prefix) {
        String value = extractTaggedValue(note, prefix);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DeliveryMode.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseIntegerTag(String note, String prefix) {
        String value = extractTaggedValue(note, prefix);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Boolean parseBooleanTag(String note, String prefix) {
        String value = extractTaggedValue(note, prefix);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private String extractTaggedValue(String note, String prefix) {
        if (note == null || note.isBlank()) {
            return null;
        }
        int start = note.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        int valueStart = start + prefix.length();
        int end = note.indexOf(']', valueStart);
        if (end < 0) {
            return null;
        }
        return note.substring(valueStart, end);
    }

    private List<String> extractRequiredEvidence(String note) {
        if (note == null || note.isBlank() || !note.contains("本轮必需证据：")) {
            return List.of();
        }
        String section = note.substring(note.indexOf("本轮必需证据：") + "本轮必需证据：".length());
        List<String> values = new ArrayList<>();
        for (String line : section.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                if (!values.isEmpty()) {
                    break;
                }
                continue;
            }
            if (trimmed.startsWith("- ")) {
                values.add(trimmed.substring(2).trim());
                continue;
            }
            if (!values.isEmpty()) {
                break;
            }
        }
        return values;
    }

    private String renderBulletList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "- (none)";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            joiner.add("- " + value.trim());
        }
        String rendered = joiner.toString();
        return rendered.isBlank() ? "- (none)" : rendered;
    }

    private boolean targetsNewEntryOrUiFiles(List<FileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return false;
        }
        for (FileChange change : changes) {
            String path = nullToEmpty(change.path()).toLowerCase();
            if (path.endsWith("index.html") || path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".js")) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldUsePreciseHtmlEditing(Path projectPath, Path relativePath, DeliveryMode deliveryMode, String existingContent) {
        if (!pathLooksLikeHtml(relativePath)) {
            return false;
        }
        if (!Files.exists(projectPath.resolve(relativePath))) {
            return false;
        }
        if (deliveryMode == DeliveryMode.SKELETON || deliveryMode == DeliveryMode.REWORK) {
            return false;
        }
        return htmlPreciseEditor.supportsPreciseEditing(existingContent);
    }

    private boolean shouldUsePreciseCodeEditing(Path projectPath, Path relativePath, DeliveryMode deliveryMode, String existingContent) {
        if (!pathLooksLikePreciseCode(relativePath)) {
            return false;
        }
        if (!Files.exists(projectPath.resolve(relativePath))) {
            return false;
        }
        if (deliveryMode == DeliveryMode.SKELETON || deliveryMode == DeliveryMode.REWORK) {
            return false;
        }
        return codePreciseEditor.supportsPreciseEditing(relativePath, existingContent);
    }

    private boolean pathLooksLikeHtml(Path relativePath) {
        String path = relativePath.toString().toLowerCase();
        return path.endsWith(".html") || path.endsWith(".htm");
    }

    private boolean pathLooksLikePreciseCode(Path relativePath) {
        String path = relativePath.toString().toLowerCase();
        return path.endsWith(".java") || path.endsWith(".py") || path.endsWith(".go")
                || path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith(".cjs")
                || path.endsWith(".ts") || path.endsWith(".tsx")
                || path.endsWith(".mts") || path.endsWith(".cts");
    }

    private int numPredictFor(DeliveryMode deliveryMode) {
        return switch (deliveryMode) {
            case SKELETON -> 1200;
            case INCREMENTAL, PATCH -> 1600;
            case REWORK -> 2200;
        };
    }

    private String renderTargetedContext(Path projectPath, List<FileChange> changes, Path currentPath) {
        if (changes == null || changes.isEmpty()) {
            return "(no related files)";
        }
        StringJoiner joiner = new StringJoiner("\n\n");
        for (FileChange change : changes) {
            Path relativePath = Path.of(change.path()).normalize();
            if (currentPath != null && relativePath.equals(currentPath)) {
                continue;
            }
            String content = Files.exists(projectPath.resolve(relativePath))
                    ? workspace.readFile(projectPath, relativePath)
                    : "(new file)";
            joiner.add("## " + relativePath + "\n\n```text\n" + summarizeForVerification(content, 12000) + "\n```");
        }
        String rendered = joiner.toString();
        return rendered.isBlank() ? "(no related files)" : rendered;
    }

    private String renderChangeList(List<FileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return "无";
        }
        return changes.stream()
                .map(change -> change.action() + " `" + change.path() + "`")
                .collect(Collectors.joining("，"));
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String trim(String content, int maxChars) {
        if (content == null || content.isBlank()) {
            return "(empty)";
        }
        return content.length() > maxChars ? content.substring(0, maxChars) + "\n...<truncated>" : content;
    }

    private String summarizeForVerification(String content, int maxChars) {
        if (content == null || content.isBlank()) {
            return "(empty)";
        }
        if (content.length() <= maxChars) {
            return content;
        }
        int half = maxChars / 2;
        return content.substring(0, half)
                + "\n...<truncated middle>...\n"
                + content.substring(content.length() - half);
    }

    private String stripCodeFence(String content) {
        String trimmed = content.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline < 0 || lastFence <= firstNewline) {
            return trimmed.replace("```", "").strip();
        }
        return trimmed.substring(firstNewline + 1, lastFence).strip();
    }

    private String validateGeneratedContent(Path projectPath, Path relativePath, String content) {
        if (content == null || content.isBlank()) {
            return "输出为空";
        }
        String path = relativePath.toString().toLowerCase();
        if (path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith(".cjs")) {
            String treeSitterFailure = validateWithTreeSitter(relativePath, content);
            if (treeSitterFailure != null) {
                return treeSitterFailure;
            }
            return validateJavaScript(projectPath, content);
        }
        if (path.endsWith(".html")) {
            String htmlFailure = validateHtmlStructure(content);
            if (htmlFailure != null) {
                return htmlFailure;
            }
            String treeSitterFailure = validateWithTreeSitter(relativePath, content);
            if (treeSitterFailure != null) {
                return treeSitterFailure;
            }
            return validateInlineScripts(projectPath, content);
        }
        if (path.endsWith(".java")) {
            return validateWithTreeSitter(relativePath, content);
        }
        if (path.endsWith(".ts") || path.endsWith(".tsx")
                || path.endsWith(".mts") || path.endsWith(".cts")) {
            return validateWithTreeSitter(relativePath, content);
        }
        if (path.endsWith(".py") || path.endsWith(".go")) {
            return validateWithTreeSitter(relativePath, content);
        }
        return null;
    }

    private String validateWithTreeSitter(Path relativePath, String content) {
        TreeSitterParseSummary summary = treeSitterSupport.analyze(relativePath, content);
        if (!summary.supported() || summary.valid()) {
            return null;
        }
        return summary.describe();
    }

    private String validateJavaScript(Path projectPath, String content) {
        try {
            Path tempFile = Files.createTempFile("devflow-generated-", ".js");
            try {
                Files.writeString(tempFile, content);
                var result = workspace.runCommand(projectPath, List.of("node", "--check", tempFile.toString()), Duration.ofMinutes(1));
                if (result.exitCode() != 0) {
                    return "JavaScript 语法检查失败: " + summarizeForVerification(result.stderr(), 400);
                }
                return null;
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception exception) {
            return "JavaScript 校验异常: " + exception.getMessage();
        }
    }

    private String validateHtmlStructure(String content) {
        String normalized = content.toLowerCase();
        if (!normalized.contains("<html") || !normalized.contains("</html>")) {
            return "HTML 结构不完整，缺少 <html> 或 </html>";
        }
        if (!normalized.contains("<body") || !normalized.contains("</body>")) {
            return "HTML 结构不完整，缺少 <body> 或 </body>";
        }
        if (countOccurrences(normalized, "<script") != countOccurrences(normalized, "</script>")) {
            return "HTML 中 <script> 标签未闭合";
        }
        if (countOccurrences(normalized, "<style") != countOccurrences(normalized, "</style>")) {
            return "HTML 中 <style> 标签未闭合";
        }
        return null;
    }

    private String validateInlineScripts(Path projectPath, String content) {
        var matcher = INLINE_SCRIPT_PATTERN.matcher(content);
        int index = 0;
        while (matcher.find()) {
            String scriptBody = matcher.group(1).trim();
            if (scriptBody.isBlank()) {
                continue;
            }
            index++;
            String failure = validateJavaScript(projectPath, scriptBody);
            if (failure != null) {
                return "内联脚本 #" + index + " 不可解析: " + failure;
            }
        }
        return null;
    }

    private int countOccurrences(String content, String token) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private FixMode extractFixMode(String note) {
        if (note == null || note.isBlank()) {
            return FixMode.NONE;
        }
        if (note.contains(FIX_MODE_REWORK_TAG)) {
            return FixMode.REWORK;
        }
        if (note.contains(FIX_MODE_PATCH_TAG)) {
            return FixMode.PATCH;
        }
        return FixMode.PATCH;
    }

    private record ImplementationPlan(
            String summary,
            List<Subtask> subtasks
    ) {
    }

    private record Subtask(
            String title,
            String goal,
            List<String> acceptanceCriteria,
            DeliveryMode deliveryMode,
            List<FileChange> changes
    ) {
    }

    private record FileChange(
            String path,
            ChangeAction action,
            String reason
    ) {
    }

    private record SubtaskExecutionReport(
            Subtask subtask,
            boolean completed,
            List<SubtaskAttemptReport> attempts
    ) {
        private String lastVerifierChangeRequest() {
            if (attempts.isEmpty()) {
                return "";
            }
            String value = attempts.get(attempts.size() - 1).review().changeRequest();
            return value == null ? "" : value;
        }
    }

    private record SubtaskAttemptReport(
            int attempt,
            SelfCheckResult selfCheck,
            ReviewResult review
    ) {
    }

    private enum ChangeAction {
        WRITE,
        DELETE
    }

    private enum DeliveryMode {
        SKELETON,
        INCREMENTAL,
        PATCH,
        REWORK
    }

    private record DeliveryPolicyEnvelope(
            DeliveryMode mode,
            int maxFiles,
            int maxSymbols,
            boolean preferPreciseEditing,
            boolean forceBacklogSplit,
            boolean requireVerificationBeforeReview,
            List<String> requiredEvidence
    ) {
    }
}
