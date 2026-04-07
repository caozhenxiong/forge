package devflow.agent.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestCasePlanner {

    private final FileProjectWorkspace workspace;
    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper;
    private final TreeSitterSupport treeSitterSupport;
    private static final Pattern LOAD_TIME_PATTERN = Pattern.compile("(加载时间|首屏|页面加载).*?(\\d+)\\s*(ms|毫秒|s|秒)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERATE_TIME_PATTERN = Pattern.compile("(生成时间|题目生成|切换时间|响应时间|回退.*响应).*?(\\d+)\\s*(ms|毫秒|s|秒)", Pattern.CASE_INSENSITIVE);

    public TestCasePlanner(FileProjectWorkspace workspace, LlmProvider llmProvider, ObjectMapper objectMapper) {
        this(workspace, llmProvider, objectMapper, new TreeSitterSupport());
    }

    TestCasePlanner(
            FileProjectWorkspace workspace,
            LlmProvider llmProvider,
            ObjectMapper objectMapper,
            TreeSitterSupport treeSitterSupport
    ) {
        this.workspace = workspace;
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
        this.treeSitterSupport = treeSitterSupport;
    }

    public TestCasePlan plan(
            Path projectPath,
            ProjectFingerprint fingerprint,
            String goal,
            String constraints,
            String prd,
            String design,
            String implementationReport
    ) {
        List<TestCaseSpec> fallback = fallbackCases(projectPath, fingerprint, prd, design, implementationReport);
        if (llmProvider == null) {
            return new TestCasePlan("未配置模型，使用默认网页测试用例。", fallback);
        }

        try {
            String context = workspace.collectContext(projectPath, 8, 2400, 9000);
            String response = llmProvider.generate(
                    """
                            你是测试用例设计器。请根据目标、PRD、技术方案和当前实现，为当前项目输出“可执行”的结构化测试用例。
                            你必须只返回 JSON，格式如下：
                            {
                              "summary": "一句话总结",
                              "cases": [
                                {
                                  "id": "TC-001",
                                  "title": "标题",
                                  "type": "smoke|functional",
                                  "required": true,
                                  "entry": "index.html",
                                  "preconditions": "前置条件，无则空字符串",
                                  "expected": "预期结果",
                                  "steps": [
                                    {
                                      "action": "ASSERT_SELECTOR|ASSERT_CANVAS_MIN|CLICK|PRESS_KEY|WAIT|ASSERT_NO_ERRORS|ASSERT_TEXT_CONTAINS|MEASURE_PAGE_LOAD_MAX_MS|ASSERT_WINDOW_METRIC_MAX_MS",
                                      "selector": "可选",
                                      "key": "可选",
                                      "count": 1,
                                      "ms": 200,
                                      "text": "可选",
                                      "optional": false
                                    }
                                  ]
                                }
                              ]
                            }

                            规则：
                            1. 只能使用给定的 action 枚举
                            2. required=true 的 case 数量控制在 2 到 5 条
                            3. 优先设计能证明“页面能跑起来、关键交互可用”的用例
                            4. 不要生成依赖外部网络、登录或人工操作的测试
                            5. 如果是网页/小游戏，至少包含页面加载、关键元素存在、无运行时错误、至少一种交互
                            6. 如果 PRD 或 DESIGN 明确提出性能指标，请补充性能 case
                            7. 页面加载耗时使用 action=MEASURE_PAGE_LOAD_MAX_MS
                            8. 如果实现通过 window.__devflowMetrics 暴露测量值，可使用 action=ASSERT_WINDOW_METRIC_MAX_MS，text 字段填指标 key
                            """,
                    """
                            目标：
                            %s

                            约束：
                            %s

                            项目特征：
                            %s

                            PRD：
                            %s

                            技术方案：
                            %s

                            实现报告：
                            %s

                            当前代码上下文：
                            %s
                            """.formatted(
                            goal,
                            blank(constraints),
                            String.join("\n", fingerprint.evidence()),
                            shrink(prd),
                            shrink(design),
                            shrink(implementationReport),
                            context
                    ),
                    Map.of("num_predict", 1200),
                    ModelRole.TEST_CASE_DESIGN
            );
            PlannedCasesPayload payload = objectMapper.readValue(extractJsonObject(response), PlannedCasesPayload.class);
            List<TestCaseSpec> planned = sanitize(payload.cases(), fallback);
            if (!planned.isEmpty()) {
                String summary = payload.summary() == null || payload.summary().isBlank()
                        ? "模型基于当前实现生成测试用例。"
                        : payload.summary();
                return new TestCasePlan(summary, planned);
            }
        } catch (Exception ignored) {
        }

        return new TestCasePlan("模型未能稳定输出测试用例，使用默认网页测试用例。", fallback);
    }

    private List<TestCaseSpec> fallbackCases(Path projectPath, ProjectFingerprint fingerprint, String prd, String design, String implementationReport) {
        List<TestCaseSpec> cases = new ArrayList<>();
        String entry = fingerprint.hasHtmlEntry() ? "index.html" : "";
        if (fingerprint.hasHtmlEntry()) {
            cases.add(new TestCaseSpec(
                    "TC-SMOKE-LOAD",
                    "页面可加载且无致命错误",
                    "smoke",
                    true,
                    entry,
                    "",
                    "页面能正常打开，且没有运行时错误。",
                    List.of(
                            new TestStepSpec("ASSERT_SELECTOR", "body", null, null, null, null, false),
                            new TestStepSpec("ASSERT_NO_ERRORS", null, null, null, null, null, false)
                    )
            ));
        }

        try {
            if (fingerprint.hasHtmlEntry()) {
                String html = workspace.readFile(projectPath, Path.of(entry));
                HtmlStructureSnapshot htmlSnapshot = treeSitterSupport.inspectHtml(html);
                if (htmlSnapshot.hasCanvas()) {
                    cases.add(new TestCaseSpec(
                            "TC-SMOKE-CANVAS",
                            "主画布存在",
                            "smoke",
                            true,
                            entry,
                            "",
                            "页面应渲染至少一个 canvas。",
                            List.of(
                                    new TestStepSpec("ASSERT_CANVAS_MIN", null, null, 1, null, null, false),
                                    new TestStepSpec("ASSERT_NO_ERRORS", null, null, null, null, null, false)
                            )
                    ));
                }

                Set<String> selectors = new LinkedHashSet<>(htmlSnapshot.buttonSelectors());
                int index = 1;
                for (String selector : selectors) {
                    if (selectors.size() > 3 && index > 3) {
                        break;
                    }
                    cases.add(new TestCaseSpec(
                            "TC-FUNC-BTN-" + index,
                            "按钮 " + selector + " 点击不报错",
                            "functional",
                            false,
                            entry,
                            "",
                            "点击按钮后页面仍保持可运行，无运行时错误。",
                            List.of(
                                    new TestStepSpec("ASSERT_SELECTOR", selector, null, null, null, null, true),
                                    new TestStepSpec("CLICK", selector, null, null, null, null, true),
                                    new TestStepSpec("WAIT", null, null, null, 150, null, false),
                                    new TestStepSpec("ASSERT_NO_ERRORS", null, null, null, null, null, false)
                            )
                    ));
                    index++;
                }

                PerformanceRequirements requirements = extractPerformanceRequirements(fingerprint, prd, design);
                if (requirements.pageLoadMs() != null) {
                    cases.add(new TestCaseSpec(
                            "TC-PERF-LOAD",
                            "页面加载耗时满足要求",
                            "performance",
                            true,
                            entry,
                            "",
                            "页面加载耗时应低于要求阈值。",
                            List.of(
                                    new TestStepSpec("MEASURE_PAGE_LOAD_MAX_MS", null, null, null, requirements.pageLoadMs(), null, false),
                                    new TestStepSpec("ASSERT_NO_ERRORS", null, null, null, null, null, false)
                            )
                    ));
                }
                if (requirements.interactionMs() != null && blank(implementationReport).contains("__devflowMetrics")) {
                    cases.add(new TestCaseSpec(
                            "TC-PERF-RUNTIME",
                            "运行时指标满足要求",
                            "performance",
                            false,
                            entry,
                            "实现需暴露 window.__devflowMetrics.lastActionMs 或等价指标。",
                            "运行时关键操作指标应低于要求阈值。",
                            List.of(
                                    new TestStepSpec("ASSERT_WINDOW_METRIC_MAX_MS", null, null, null, requirements.interactionMs(), "lastActionMs", false),
                                    new TestStepSpec("ASSERT_NO_ERRORS", null, null, null, null, null, false)
                            )
                    ));
                }
            }
        } catch (Exception ignored) {
        }

        if (cases.isEmpty()) {
            cases.add(new TestCaseSpec(
                    "TC-BASIC",
                    "基础自检用例",
                    "smoke",
                    true,
                    entry,
                    "",
                    "至少执行一轮基础检查。",
                    List.of(List.of(new TestStepSpec("ASSERT_NO_ERRORS", null, null, null, null, null, false))).getFirst()
            ));
        }
        return cases;
    }

    private List<TestCaseSpec> sanitize(List<PlannedCasePayload> rawCases, List<TestCaseSpec> fallback) {
        if (rawCases == null || rawCases.isEmpty()) {
            return fallback;
        }
        List<TestCaseSpec> result = new ArrayList<>();
        for (PlannedCasePayload raw : rawCases) {
            if (raw == null || raw.id == null || raw.id.isBlank() || raw.title == null || raw.title.isBlank()) {
                continue;
            }
            List<TestStepSpec> steps = sanitizeSteps(raw.steps());
            if (steps.isEmpty()) {
                continue;
            }
            result.add(new TestCaseSpec(
                    raw.id().trim(),
                    raw.title().trim(),
                    blank(raw.type()).isBlank() ? "smoke" : raw.type().trim(),
                    raw.required() == null || raw.required(),
                    blank(raw.entry()).isBlank() ? "index.html" : raw.entry().trim(),
                    blank(raw.preconditions()),
                    blank(raw.expected()),
                    steps
            ));
        }
        return result.isEmpty() ? fallback : result;
    }

    private List<TestStepSpec> sanitizeSteps(List<PlannedStepPayload> rawSteps) {
        if (rawSteps == null || rawSteps.isEmpty()) {
            return List.of();
        }
        List<TestStepSpec> result = new ArrayList<>();
        for (PlannedStepPayload raw : rawSteps) {
            if (raw == null || raw.action == null || raw.action.isBlank()) {
                continue;
            }
            String action = raw.action().trim();
            if (!Set.of(
                    "ASSERT_SELECTOR",
                    "ASSERT_CANVAS_MIN",
                    "CLICK",
                    "PRESS_KEY",
                    "WAIT",
                    "ASSERT_NO_ERRORS",
                    "ASSERT_TEXT_CONTAINS",
                    "MEASURE_PAGE_LOAD_MAX_MS",
                    "ASSERT_WINDOW_METRIC_MAX_MS"
            ).contains(action)) {
                continue;
            }
            result.add(new TestStepSpec(
                    action,
                    blank(raw.selector()),
                    blank(raw.key()),
                    raw.count(),
                    raw.ms(),
                    blank(raw.text()),
                    raw.optional() != null && raw.optional()
            ));
        }
        return result;
    }

    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private String shrink(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() > 6000 ? value.substring(0, 6000) + "\n...<truncated>" : value;
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private PerformanceRequirements extractPerformanceRequirements(ProjectFingerprint fingerprint, String prd, String design) {
        String combined = String.join("\n", blank(prd), blank(design), String.join("\n", fingerprint.evidence()));
        Integer pageLoadMs = firstThresholdMs(LOAD_TIME_PATTERN, combined);
        Integer interactionMs = firstThresholdMs(GENERATE_TIME_PATTERN, combined);
        return new PerformanceRequirements(pageLoadMs, interactionMs);
    }

    private Integer firstThresholdMs(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        int value = Integer.parseInt(matcher.group(2));
        String unit = matcher.group(3);
        if (unit != null && ("s".equalsIgnoreCase(unit) || "秒".equals(unit))) {
            return value * 1000;
        }
        return value;
    }

    private record PlannedCasesPayload(
            @JsonProperty("summary") String summary,
            @JsonProperty("cases") List<PlannedCasePayload> cases
    ) {
    }

    private record PlannedCasePayload(
            @JsonProperty("id") String id,
            @JsonProperty("title") String title,
            @JsonProperty("type") String type,
            @JsonProperty("required") Boolean required,
            @JsonProperty("entry") String entry,
            @JsonProperty("preconditions") String preconditions,
            @JsonProperty("expected") String expected,
            @JsonProperty("steps") List<PlannedStepPayload> steps
    ) {
    }

    private record PlannedStepPayload(
            @JsonProperty("action") String action,
            @JsonProperty("selector") String selector,
            @JsonProperty("key") String key,
            @JsonProperty("count") Integer count,
            @JsonProperty("ms") Integer ms,
            @JsonProperty("text") String text,
            @JsonProperty("optional") Boolean optional
    ) {
    }

    private record PerformanceRequirements(
            Integer pageLoadMs,
            Integer interactionMs
    ) {
    }
}
