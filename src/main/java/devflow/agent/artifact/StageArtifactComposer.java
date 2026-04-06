package devflow.agent.artifact;

import devflow.agent.executor.ImplementationExecutor;
import devflow.agent.executor.LlmProvider;
import devflow.agent.executor.ModelRole;
import devflow.agent.executor.TestExecutor;
import devflow.agent.executor.TestExecutionBundle;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageExecution;
import devflow.agent.orchestrator.StageType;
import devflow.agent.project.WorkspaceSnapshotStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StageArtifactComposer {

    private final ArtifactTemplateFactory artifactTemplateFactory;
    private final FileArtifactStore artifactStore;
    private final LlmProvider llmProvider;
    private final ImplementationExecutor implementationExecutor;
    private final TestExecutor testExecutor;
    private final WorkspaceSnapshotStore snapshotStore;

    public StageArtifactComposer(
            ArtifactTemplateFactory artifactTemplateFactory,
            FileArtifactStore artifactStore,
            LlmProvider llmProvider,
            ImplementationExecutor implementationExecutor,
            TestExecutor testExecutor,
            WorkspaceSnapshotStore snapshotStore
    ) {
        this.artifactTemplateFactory = artifactTemplateFactory;
        this.artifactStore = artifactStore;
        this.llmProvider = llmProvider;
        this.implementationExecutor = implementationExecutor;
        this.testExecutor = testExecutor;
        this.snapshotStore = snapshotStore;
    }

    public String compose(Path projectPath, RunRecord runRecord, StageType stageType, String note) {
        return switch (stageType) {
            case ANALYSIS -> generateAnalysis(runRecord, note);
            case PRD -> generatePrd(projectPath, runRecord, note);
            case DESIGN -> generateDesign(projectPath, runRecord, note);
            case IMPLEMENTATION -> generateImplementation(projectPath, runRecord, note);
            case CODE_REVIEW -> generateCodeReview(projectPath, runRecord, note);
            case TEST -> generateTest(projectPath, runRecord, note);
        };
    }

    private String generateAnalysis(RunRecord runRecord, String note) {
        String template = artifactTemplateFactory.create(StageType.ANALYSIS, runRecord, note);
        String previousDraft = currentStageArtifactOrEmpty(runRecord, StageType.ANALYSIS);
        List<String> missingSections = extractRequestedSections(note);
        String previousOutline = documentOutline(previousDraft);
        DocumentDraftMode mode = resolveDocumentDraftMode(previousDraft, missingSections);
        String system = """
                你是一个资深需求分析师。请输出结构清晰、内容完整、面向研发落地的 Markdown 文档。
                你必须严格遵循给定模板的章节结构和顺序，不要增删主章节，不要输出多余前言，不要解释你自己。
                """;
        String user = switch (mode) {
            case FULL_DRAFT -> """
                    请根据以下信息输出《需求分析与调研》文档。

                    目标：
                    %s

                    约束：
                    %s

                    当前备注：
                    %s

                    上一轮草稿（如果为空表示首次生成）：
                    %s

                    输出要求：
                    1. 使用 Markdown
                    2. 标题为“# 需求分析与调研”
                    3. 必须严格按照下面模板输出，保留章节编号和标题
                    4. 每个二级章节都要有实质内容，不能留空、不能写 [TODO]
                    5. 内容要具体，避免空泛表述；能量化的成功标准尽量量化
                    6. “明确不做”和“待确认问题”必须写出来
                    7. 如果当前备注里包含修订意见，优先保留上一轮已经合格的章节内容，只补齐缺失或不合格章节，再输出完整文档
                    8. 不要因为修订某几个章节而删掉前面已经写好的章节
                    9. 优先保证所有主章节都完整出现，再补充细节，不要在前几个章节写得过长导致后面章节缺失

                    输出模板：
                    %s
                    """.formatted(runRecord.goal(), blankIfNull(runRecord.constraints()), note, blankIfNull(previousDraft), template);
            case FILL_MISSING_SECTIONS -> """
                    请基于上一轮《需求分析与调研》草稿，只补齐下列缺失章节，然后输出这些章节的完整 Markdown 内容。

                    目标：
                    %s

                    约束：
                    %s

                    当前备注：
                    %s

                    上一轮草稿章节提纲：
                    %s

                    需要补齐的章节：
                    %s

                    输出要求：
                    1. 只输出上述缺失章节，不要重写整篇文档
                    2. 每个章节都必须保留原编号和标题
                    3. 每个章节都要包含足够内容，不能写 [TODO]
                    4. 输出顺序必须与文档顺序一致
                    5. 每个缺失章节优先用紧凑的要点列表，不要写成长篇散文
                    """.formatted(runRecord.goal(), blankIfNull(runRecord.constraints()), note, blankIfNull(previousOutline), String.join("、", missingSections));
            case REVISE_WITH_EXISTING_DRAFT -> """
                    请基于上一轮《需求分析与调研》草稿进行修订，并输出需要替换的顶层章节完整 Markdown 内容。

                    目标：
                    %s

                    约束：
                    %s

                    当前备注：
                    %s

                    上一轮草稿章节提纲：
                    %s

                    输出要求：
                    1. 只输出需要修订的顶层章节，不要重写整篇文档
                    2. 每个章节都必须保留原编号和标题
                    3. 修订重点是解决当前备注指出的逻辑矛盾、信息缺失或表达问题
                    4. 不要删除未被修订的章节，程序会把你输出的章节合并回旧稿
                    5. 输出顺序必须与文档顺序一致
                    """.formatted(runRecord.goal(), blankIfNull(runRecord.constraints()), note, blankIfNull(previousOutline));
        };
        ModelRole generationRole = ModelRole.ANALYSIS;
        String generated = llmProvider.generate(system, user, Map.of("num_predict", mode == DocumentDraftMode.FULL_DRAFT ? 2200 : 900), generationRole);
        return mergeDocumentDraft(StageType.ANALYSIS, previousDraft, generated, missingSections);
    }

    private String generatePrd(Path projectPath, RunRecord runRecord, String note) {
        String analysis = requiredStageArtifact(projectPath, runRecord, StageType.ANALYSIS);
        String template = artifactTemplateFactory.create(StageType.PRD, runRecord, note);
        String previousDraft = currentStageArtifactOrEmpty(runRecord, StageType.PRD);
        List<String> missingSections = extractRequestedSections(note);
        String previousOutline = documentOutline(previousDraft);
        DocumentDraftMode mode = resolveDocumentDraftMode(previousDraft, missingSections);
        String system = """
                你是一个资深产品经理。请把输入材料整理成面向研发执行的 PRD，内容具体、可验证、可拆解。
                你必须严格遵循给定模板的章节结构和顺序，不要增删主章节，不要输出多余解释。
                """;
        String user = switch (mode) {
            case FULL_DRAFT -> """
                    基于下面的需求分析内容，输出《产品需求文档》。

                    输入材料：
                    %s

                    当前备注：
                    %s

                    上一轮草稿（如果为空表示首次生成）：
                    %s

                    输出要求：
                    1. 使用 Markdown
                    2. 标题为“# 产品需求文档”
                    3. 必须严格按照下面模板输出，保留章节编号和标题
                    4. 功能范围要分“核心功能 / 辅助功能 / 异常与边界场景”
                    5. 验收标准必须可测、可执行，不能写成口号
                    6. “不做什么”必须明确列出
                    7. 不要留 [TODO]
                    8. 如果当前备注里包含修订意见，优先保留上一轮已经合格的章节内容，只补齐缺失或不合格章节，再输出完整 PRD
                    9. 不要因为补某个章节而删掉已经存在的章节
                    10. 先确保 1-6 所有主章节都完整出现，再扩展细节；不要在前面章节写得过长导致后续章节缺失
                    11. 每个三级小节优先使用 2-4 条高信息密度条目，不要写成长篇散文

                    输出模板：
                    %s
                    """.formatted(analysis, note, blankIfNull(previousDraft), template);
            case FILL_MISSING_SECTIONS -> """
                    基于下面的需求分析和上一轮 PRD 草稿，只补齐缺失章节，不要重写整篇文档。

                    输入材料：
                    %s

                    当前备注：
                    %s

                    上一轮草稿章节提纲：
                    %s

                    需要补齐的章节：
                    %s

                    输出要求：
                    1. 只输出上述缺失章节，不要重复输出已合格章节
                    2. 每个章节保留原编号和标题
                    3. 每个章节内容要具体、可执行、可验收
                    4. 输出顺序必须与文档顺序一致
                    5. 每个缺失章节优先用高信息密度条目，不要扩写整篇背景
                    """.formatted(analysis, note, blankIfNull(previousOutline), String.join("、", missingSections));
            case REVISE_WITH_EXISTING_DRAFT -> """
                    基于下面的需求分析和上一轮 PRD 草稿，输出需要替换的顶层章节完整 Markdown 内容。

                    输入材料：
                    %s

                    当前备注：
                    %s

                    上一轮草稿章节提纲：
                    %s

                    输出要求：
                    1. 只输出需要修订的顶层章节，不要重写整篇 PRD
                    2. 每个章节保留原编号和标题
                    3. 修订重点是解决当前备注指出的逻辑矛盾、信息缺失或表达不清问题
                    4. 不要删除未被修订的章节，程序会把你输出的章节合并回旧稿
                    5. 输出顺序必须与文档顺序一致
                    """.formatted(analysis, note, blankIfNull(previousOutline));
        };
        ModelRole generationRole = ModelRole.PRD;
        String generated = llmProvider.generate(system, user, Map.of("num_predict", mode == DocumentDraftMode.FULL_DRAFT ? 2200 : 900), generationRole);
        return mergeDocumentDraft(StageType.PRD, previousDraft, generated, missingSections);
    }

    private String generateDesign(Path projectPath, RunRecord runRecord, String note) {
        String prd = requiredStageArtifact(projectPath, runRecord, StageType.PRD);
        String template = artifactTemplateFactory.create(StageType.DESIGN, runRecord, note);
        String previousDraft = currentStageArtifactOrEmpty(runRecord, StageType.DESIGN);
        List<String> missingSections = extractRequestedSections(note);
        String previousOutline = documentOutline(previousDraft);
        DocumentDraftMode mode = resolveDocumentDraftMode(previousDraft, missingSections);
        String system = """
                你是一个资深架构师。请把 PRD 转成面向工程实现的技术方案，要求结构化、具体、可实施。
                你必须严格遵循给定模板的章节结构和顺序，不要增删主章节，不要输出额外解释。
                """;
        String performanceGuidance = designPerformanceGuidance(runRecord, prd);
        String user = switch (mode) {
            case FULL_DRAFT -> """
                    基于下面的 PRD，输出《技术方案设计》。

                    输入材料：
                    %s

                    当前备注：
                    %s

                    上一轮草稿（如果为空表示首次生成）：
                    %s

                    输出要求：
                    1. 使用 Markdown
                    2. 标题为“# 技术方案设计”
                    3. 必须严格按照下面模板输出，保留章节编号和标题
                    4. 模块划分、数据模型、关键流程必须尽量落到当前项目上下文
                    5. 测试与验证策略必须写出具体方法，不要只写“补充测试”
                    6. 风险与取舍必须明确说明为什么这样选
                    7. 不要留 [TODO]
                    8. 如果当前备注里包含修订意见，优先保留上一轮已经合格的章节内容，只补齐缺失或不合格章节，再输出完整技术方案
                    9. 不要因为修订某一节而删掉其他已存在章节
                    10. 先确保所有主章节都完整出现，再扩展细节；不要在前几个章节写得过长导致后面章节缺失
                    11. %s

                    输出模板：
                    %s
                    """.formatted(prd, note, blankIfNull(previousDraft), performanceGuidance, template);
            case FILL_MISSING_SECTIONS -> """
                    基于下面的 PRD 和上一轮《技术方案设计》草稿，只补齐缺失章节，不要重写整篇文档。

                    输入材料：
                    %s

                    当前备注：
                    %s

                    上一轮草稿章节提纲：
                    %s

                    需要补齐的章节：
                    %s

                    输出要求：
                    1. 只输出上述缺失章节，不要重复输出其他章节
                    2. 每个章节保留原编号和标题
                    3. 每个章节都要落到工程实现层面
                    4. 输出顺序必须与文档顺序一致
                    5. 缺失章节优先直接给出方案要点，不要重复扩写已有章节
                    6. %s
                    """.formatted(prd, note, blankIfNull(previousOutline), String.join("、", missingSections), performanceGuidance);
            case REVISE_WITH_EXISTING_DRAFT -> """
                    基于下面的 PRD 和上一轮《技术方案设计》草稿，输出需要替换的顶层章节完整 Markdown 内容。

                    输入材料：
                    %s

                    当前备注：
                    %s

                    上一轮草稿章节提纲：
                    %s

                    输出要求：
                    1. 只输出需要修订的顶层章节，不要重写整篇技术方案
                    2. 每个章节保留原编号和标题
                    3. 修订重点是解决当前备注指出的方案逻辑、接口定义或验证策略问题
                    4. 不要删除未被修订的章节，程序会把你输出的章节合并回旧稿
                    5. 输出顺序必须与文档顺序一致
                    6. %s
                    """.formatted(prd, note, blankIfNull(previousOutline), performanceGuidance);
        };
        ModelRole generationRole = ModelRole.DESIGN;
        String generated = llmProvider.generate(system, user, Map.of("num_predict", mode == DocumentDraftMode.FULL_DRAFT ? 2400 : 1000), generationRole);
        return mergeDocumentDraft(StageType.DESIGN, previousDraft, generated, missingSections);
    }

    private String generateImplementation(Path projectPath, RunRecord runRecord, String note) {
        String analysis = requiredStageArtifact(projectPath, runRecord, StageType.ANALYSIS);
        String prd = requiredStageArtifact(projectPath, runRecord, StageType.PRD);
        String design = requiredStageArtifact(projectPath, runRecord, StageType.DESIGN);
        return implementationExecutor.execute(projectPath, runRecord, analysis, prd, design, note);
    }

    private String generateCodeReview(Path projectPath, RunRecord runRecord, String note) {
        String implementation = requiredStageArtifact(projectPath, runRecord, StageType.IMPLEMENTATION);
        String changes = snapshotStore.renderChanges(projectPath, runRecord.runId(), 8, 5000);
        String system = """
                你是严格的资深代码审阅者。请结合实现报告和实际代码变更做 code review。
                输出 Markdown，不要输出额外解释。
                第一部分必须包含以下六行：
                - decision: APPROVED|REVISION_REQUIRED|REJECTED
                - fixMode: NONE|PATCH|REWORK
                - summary: 一句话总结
                - changeRequest: 如需修改则写清楚，否则留空
                - evidence: 写最关键的代码/测试证据，否则留空
                - actionItems: 写 coder 可直接执行的动作，否则留空

                fixMode 规则：
                - APPROVED 时必须是 NONE
                - PATCH 表示结构基本可接受，只做增量修补
                - REWORK 表示结构存在明显问题，允许较大范围重构

                审阅约束：
                - Findings 只能写你能从“实际代码变更”或“实现报告”中直接证实的问题
                - 不要猜测“可能缺少某功能”，除非代码里确实没有对应实现证据
                - 如果 decision=APPROVED，则 Findings 必须为空，或者只写“无阻塞问题”
                - 不要因为空目录、历史残留说明或已经删除的模块名称，就判定当前代码仍存在结构问题
                - 如果有问题，必须优先给出“具体问题”，不能只写空泛评价
                - 每条问题尽量落到具体文件、函数、变量、事件绑定、资源引用或测试证据
                - changeRequest 必须是 coder 可直接执行的修复动作，不要写成“请自行排查”
                """;
        String user = """
                当前备注：
                %s

                实现报告：
                %s

                实际代码变更：
                %s

                输出要求：
                1. 必须先输出 decision / fixMode / summary / changeRequest / evidence / actionItems 六行
                2. 然后给出 Findings
                3. Findings 只列真正的问题和风险，并尽量引用具体文件/代码证据
                4. 每条 Findings 用下面格式：
                   - [严重度] 文件或模块：具体问题。证据：xxx。建议：xxx。
                5. 如果 decision 不是 APPROVED，summary / changeRequest / evidence / actionItems 都必须概括最关键的 1-2 个具体问题
                """.formatted(note, implementation, changes);
        return llmProvider.generate(system, user, Map.of("num_predict", 1400), ModelRole.CODE_REVIEW);
    }

    private String generateTest(Path projectPath, RunRecord runRecord, String note) {
        String prd = requiredStageArtifact(projectPath, runRecord, StageType.PRD);
        String design = requiredStageArtifact(projectPath, runRecord, StageType.DESIGN);
        String implementation = requiredStageArtifact(projectPath, runRecord, StageType.IMPLEMENTATION);
        TestExecutionBundle bundle = testExecutor.execute(
                projectPath,
                runRecord.goal(),
                runRecord.constraints(),
                prd,
                design,
                implementation,
                note
        );
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), "test_cases.md", bundle.testCasesMarkdown());
        artifactStore.writeAuxiliaryArtifact(projectPath, runRecord.runId(), "test_execution.md", bundle.executionMarkdown());
        return bundle.reportMarkdown();
    }

    private String requiredStageArtifact(Path projectPath, RunRecord runRecord, StageType stageType) {
        StageExecution stageExecution = runRecord.stageStates().get(stageType);
        if (stageExecution == null || stageExecution.artifactPath() == null) {
            throw new IllegalStateException("Missing upstream artifact for stage " + stageType);
        }
        return artifactStore.readArtifact(projectPath, runRecord.runId(), stageType);
    }

    private String currentStageArtifactOrEmpty(RunRecord runRecord, StageType stageType) {
        StageExecution stageExecution = runRecord.stageStates().get(stageType);
        if (stageExecution == null || stageExecution.artifactPath() == null) {
            return "";
        }
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(stageExecution.artifactPath()));
        } catch (Exception exception) {
            return "";
        }
    }

    private List<String> extractRequestedSections(String note) {
        if (note == null || note.isBlank()) {
            return List.of();
        }
        Matcher matcher = Pattern.compile("##\\s*\\d+\\.\\s*[^、，\\n]+").matcher(note);
        List<String> sections = new ArrayList<>();
        while (matcher.find()) {
            sections.add(matcher.group().trim());
        }
        return sections;
    }

    private DocumentDraftMode resolveDocumentDraftMode(String previousDraft, List<String> missingSections) {
        if (previousDraft == null || previousDraft.isBlank()) {
            return DocumentDraftMode.FULL_DRAFT;
        }
        if (!missingSections.isEmpty()) {
            return DocumentDraftMode.FILL_MISSING_SECTIONS;
        }
        return DocumentDraftMode.REVISE_WITH_EXISTING_DRAFT;
    }

    private String mergeDocumentDraft(StageType stageType, String previousDraft, String generated, List<String> missingSections) {
        if (previousDraft == null || previousDraft.isBlank()) {
            return generated;
        }
        List<String> orderedSections = topLevelSections(stageType);
        String merged = previousDraft;
        List<String> targetSections = missingSections.isEmpty() ? orderedSections : missingSections;
        for (String heading : targetSections) {
            String block = extractSectionBlock(generated, heading, orderedSections);
            if (block == null || block.isBlank()) {
                continue;
            }
            merged = upsertSectionBlock(merged, heading, block.trim(), orderedSections);
        }
        return merged;
    }

    private List<String> topLevelSections(StageType stageType) {
        return switch (stageType) {
            case ANALYSIS -> List.of(
                    "## 1. 背景与问题定义",
                    "## 2. 目标与成功标准",
                    "## 3. 关键约束",
                    "## 4. 初步调研与假设",
                    "## 5. 边界与非目标",
                    "## 6. 风险与待确认问题",
                    "## 7. 当前备注"
            );
            case PRD -> List.of(
                    "## 1. 产品目标",
                    "## 2. 目标用户与使用场景",
                    "## 3. 功能范围",
                    "## 4. 非功能要求",
                    "## 5. 验收标准",
                    "## 6. 不做什么",
                    "## 7. 当前备注"
            );
            case DESIGN -> List.of(
                    "## 1. 技术目标",
                    "## 2. 系统边界与模块划分",
                    "## 3. 核心数据模型",
                    "## 4. 关键流程",
                    "## 5. 接口、页面或命令设计",
                    "## 6. 测试与验证策略",
                    "## 7. 风险与取舍",
                    "## 8. 当前备注"
            );
            default -> List.of();
        };
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

    private String upsertSectionBlock(String content, String heading, String newBlock, List<String> orderedSections) {
        String existing = extractSectionBlock(content, heading, orderedSections);
        if (existing != null) {
            return content.replace(existing, newBlock);
        }
        int currentIndex = orderedSections.indexOf(heading);
        for (int i = currentIndex + 1; i < orderedSections.size(); i++) {
            int anchor = content.indexOf(orderedSections.get(i));
            if (anchor >= 0) {
                return content.substring(0, anchor).stripTrailing() + "\n\n" + newBlock + "\n\n" + content.substring(anchor).stripLeading();
            }
        }
        return content.stripTrailing() + "\n\n" + newBlock + "\n";
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private String designPerformanceGuidance(RunRecord runRecord, String prd) {
        String upstream = String.join("\n",
                blankIfNull(runRecord.goal()),
                blankIfNull(runRecord.constraints()),
                blankIfNull(prd)
        );
        Pattern explicitPerformanceRequirement = Pattern.compile(
                "(\\d+\\s*(ms|毫秒|s|秒)|p95|benchmark|基准|基准测试|性能验收|响应时间|生成时间|耗时|加载时间|切换时间|延迟|吞吐)",
                Pattern.CASE_INSENSITIVE
        );
        if (explicitPerformanceRequirement.matcher(upstream).find()) {
            return "如果上游需求已明确性能目标或测量要求，请在技术方案中定义相应的基础测量方法、阈值和执行阶段。";
        }
        return "如果上游需求没有明确性能目标，不要自行发明量化性能指标、benchmark 阈值或强制性能验收；测试与验证策略应以功能正确性、可运行性和基础回归验证为主。";
    }

    private String documentOutline(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : markdown.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.matches("^#{1,6}\\s+.+$")) {
                builder.append(trimmed).append('\n');
            }
        }
        return builder.toString().trim();
    }
}
