package devflow.agent.executor;

import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractView;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.QualityPlan;
import devflow.agent.validation.ProjectFingerprint;
import java.nio.file.Path;
import java.util.LinkedHashSet;

/**
 * 负责测试规划提示词组装。
 *
 * <p>它只负责：
 * 1. 收集结构化契约与代码上下文；
 * 2. 生成 testcase planner 的 system/user prompt。
 *
 * <p>它不负责：
 * 1. 调模型；
     * 2. 生成确定性基础测试约束；
 * 3. 清洗模型输出。
 */
final class TestCasePromptAssembler {

    private final FileProjectWorkspace workspace;
    private final ContractExtractor contractExtractor;

    TestCasePromptAssembler(FileProjectWorkspace workspace, ContractExtractor contractExtractor) {
        this.workspace = workspace;
        this.contractExtractor = contractExtractor;
    }

    TestCaseGenerationPrompt assemble(
            Path projectPath,
            ProjectFingerprint fingerprint,
            String goal,
            String constraints,
            String prd,
            String design,
            String implementationReport,
            RuntimeSnapshot runtimeSnapshot,
            QualityPlan qualityPlan,
            UiRuntimeContract runtimeContract
    ) {
        ContractView contractView = contractExtractor.extractContractView(goal, constraints, "", prd, design);
        String context = workspace.collectContext(projectPath, 8, 2400, 9000);
        String requiredCoverageInstruction = requiredCoverageInstruction(qualityPlan);
        String systemPrompt = """
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
                      "entry": "实际入口相对路径，例如 public/index.html",
                      "preconditions": "前置条件，无则空字符串",
                      "expected": "预期结果",
                      "capabilities": ["%s"],
                      "observationTargetId": "primary-visual-surface|primary-interaction|空字符串",
                      "observationTrigger": "NONE|AFTER_INTERACTION|AFTER_WAIT",
                      "observationComparison": "NONE|CHANGED|UNCHANGED",
                      "steps": [
                        {
                          "action": "%s",
                          "selector": "可选",
                          "key": "可选",
                          "count": 1,
                          "ms": 200,
                          "text": "可选",
                          "optional": false,
                          "semantic": "%s"
                        }
                      ]
                    }
                  ]
                }

                规则：
                1. 只能使用给定的 action 枚举
                2. required=true 的 case 数量必须足以覆盖 capability matrix 中 expectation=REQUIRED 的能力项；允许一个 case 覆盖多个能力，但不得遗漏 required capability
                3. 优先设计能证明“入口可运行、主要运行表面存在、关键能力可观察”的用例
                4. 不要生成依赖外部网络、登录或人工操作的测试
                5. 结合 feature profile 和 capability matrix，为所有 required capability surface 设计 required case，并确保每个 required case 都有明确观测点
                6. 任何 required 的交互用例（包含 CLICK 或 PRESS_KEY）都必须包含“可观察的后置状态变化”，不能只点击/按键后检查不报错
                7. 优先使用通用观测动作证明状态变化：SNAPSHOT_CANVAS_HASH / ASSERT_CANVAS_HASH_CHANGED 或 SNAPSHOT_DOM_SIGNATURE / ASSERT_DOM_SIGNATURE_CHANGED
                8. 如果交互没有明确可观察的后置变化，则该用例不合格
                9. 如果 PRD 或 DESIGN 明确提出性能指标，请补充性能 case
                10. 页面加载耗时使用 action=MEASURE_PAGE_LOAD_MAX_MS
                11. 如果实现通过 %s 暴露测量值，可使用 action=%s，text 字段填指标 key
                12. capabilities 只能使用给定 capability catalog 中的值
                13. required 的 testcase 必须优先覆盖 capability matrix 中 expectation=REQUIRED 的能力项
                14. 如果某个步骤承担“进入运行态 / 暂停切换 / 状态重置 / 主交互控件 / 主观测面 / 进度信号”语义，必须填写 semantic
                15. 不要依赖 selector 文本暗示语义；请直接用 semantic 明确声明
                16. 只有运行时观测契约中的 runStateEntryTargets，或运行时快照里显式暴露的 control candidate，才允许使用 semantic=run-state-entry；禁止把 body、main 或其他宿主根容器写成 run-state-entry
                17. 如果运行时观测契约没有 runStateEntryTargets，且运行时快照也没有显式 control candidate，就不要发明 click 型启动步骤
                18. 只要 case 需要 runtime 对比观察，就必须显式填写 observationTargetId / observationTrigger / observationComparison；repair/gate 只认这三个字段，不会再从 capability 名称或 prose 猜语义
                19. observationTargetId 只能使用 %s
                20. %s
                """.formatted(
                CapabilityIds.PAGE_LOAD,
                TestStepAction.wireCatalog(),
                TestStepSemantic.RUN_STATE_ENTRY.wireValue(),
                WebRuntimeMetricKeys.PUBLIC_METRICS_OBJECT,
                TestStepAction.ASSERT_WINDOW_METRIC_MAX_MS.name(),
                builtinObservationTargetCatalog(),
                requiredCoverageInstruction
        );
        String userPrompt = """
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

                结构化契约：
                %s

                产品需求覆盖引用：
                %s

                质量计划：
                %s

                required capability surfaces：
                %s

                capability catalog：
                %s

                observation target catalog：
                %s

                step semantic catalog：
                %s

                实现报告：
                %s

                运行时快照：
                %s

                运行时观测契约：
                %s

                当前代码上下文：
                %s
                """.formatted(
                goal,
                blank(constraints),
                String.join("\n", fingerprint.evidence()),
                shrink(prd),
                shrink(design),
                contractView == null ? "" : shrink(contractView.toMarkdown(devflow.agent.i18n.DocumentLanguage.detect(goal, constraints))),
                contractView == null ? PlaceholderValues.none(devflow.agent.i18n.DocumentLanguage.detect(goal, constraints))
                        : shrink(contractView.productRequirementCatalogMarkdown(devflow.agent.i18n.DocumentLanguage.detect(goal, constraints))),
                qualityPlan == null ? "" : shrink(qualityPlan.toMarkdown(devflow.agent.i18n.DocumentLanguage.detect(goal, constraints))),
                requiredCapabilitySurfaceCatalog(qualityPlan),
                capabilityCatalog(qualityPlan),
                builtinObservationTargetCatalog(),
                TestStepSemantic.wireCatalog(),
                shrink(implementationReport),
                runtimeSnapshot == null ? "" : runtimeSnapshot.toMarkdown(devflow.agent.i18n.DocumentLanguage.detect(goal, constraints)),
                runtimeContract == null ? "" : runtimeContract.toMarkdown(devflow.agent.i18n.DocumentLanguage.detect(goal, constraints)),
                context
        );
        return new TestCaseGenerationPrompt(systemPrompt, userPrompt);
    }

    private String shrink(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return PlaceholderValues.truncateTail(value, 6000);
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    private String requiredCoverageInstruction(QualityPlan qualityPlan) {
        if (qualityPlan == null || qualityPlan.capabilityMatrix().requiredCapabilityIds().isEmpty()) {
            return "如果 capability matrix 没有 required capability，也至少保留 page-load、runtime-stability 和 primary observation 的 smoke coverage。";
        }
        return "本轮至少覆盖这些 required capability surface："
                + requiredCapabilitySurfaceCatalog(qualityPlan)
                + "。如果某个 capability 已由别的用例覆盖，可以复用，不要再凭产品类型追加特判。";
    }

    private String requiredCapabilitySurfaceCatalog(QualityPlan qualityPlan) {
        if (qualityPlan == null || qualityPlan.capabilityMatrix().requiredCapabilityIds().isEmpty()) {
            return "(none)";
        }
        return qualityPlan.capabilityMatrix().requiredCapabilityIds().stream()
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("(none)");
    }

    private String capabilityCatalog(QualityPlan qualityPlan) {
        LinkedHashSet<String> catalog = new LinkedHashSet<>();
        catalog.addAll(java.util.List.of(
                CapabilityIds.PAGE_LOAD,
                CapabilityIds.RUNTIME_STABILITY,
                CapabilityIds.PRIMARY_VISUAL_SURFACE,
                CapabilityIds.PRIMARY_INTERACTION,
                CapabilityIds.PERFORMANCE_LOAD,
                CapabilityIds.PERFORMANCE_INTERACTION
        ));
        if (qualityPlan != null && qualityPlan.capabilityMatrix() != null) {
            catalog.addAll(qualityPlan.capabilityMatrix().requiredCapabilityIds());
        }
        return String.join("|", catalog);
    }

    private String builtinObservationTargetCatalog() {
        return CapabilityIds.PRIMARY_VISUAL_SURFACE + "|" + CapabilityIds.PRIMARY_INTERACTION;
    }
}
