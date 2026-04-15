package devflow.agent.executor.testing;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.llm.LlmProvider;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.SubtaskVerificationOutcome;

import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.quality.CapabilityExpectation;
import devflow.agent.quality.CapabilityIds;
import devflow.agent.quality.CapabilityMatrix;
import devflow.agent.quality.CapabilityMatrixEntry;
import devflow.agent.quality.CoveragePolicy;
import devflow.agent.quality.ExperiencePolicy;
import devflow.agent.quality.FeatureProfile;
import devflow.agent.quality.QualityChecklist;
import devflow.agent.quality.QualityIntent;
import devflow.agent.quality.QualityPlan;
import devflow.agent.quality.StructurePolicy;
import devflow.agent.quality.StructureRiskLevel;
import devflow.agent.quality.StructureRiskReport;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewReasonCode;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewRevisionRoute;
import devflow.agent.validation.ProjectFingerprint;
import devflow.agent.validation.ProjectInspector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
class TestExecutorTests {

    @TempDir
    Path tempDir;

    @Test
    void staticWebCheckAcceptsThisPropertyAndInlineClickHandler() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <button id="restartButton">重新开始</button>
                          <script>
                            class GameController {
                              constructor() {
                                this.restartButton = document.getElementById('restartButton');
                                this.bindEvents();
                              }

                              bindEvents() {
                                this.restartButton.addEventListener('click', () => {
                                  this.restart();
                                });
                              }

                              restart() {}
                            }
                          </script>
                        </body>
                        </html>
                        """
        );

        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("验证策略规划器")) {
                    return """
                            {
                              "summary": "对静态网页项目执行资源与脚本语法检查。",
                              "steps": [
                                {
                                  "capability": "WEB_RESOURCE_LINK_CHECK",
                                  "reason": "先确认本地资源引用完整。",
                                  "required": true
                                },
                                {
                                  "capability": "WEB_JAVASCRIPT_SYNTAX_CHECK",
                                  "reason": "再确认脚本语法可执行。",
                                  "required": true
                                }
                              ]
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(new FileProjectWorkspace(), provider, new com.fasterxml.jackson.databind.ObjectMapper());
        SelfCheckResult result = executor.selfCheck(tempDir);

        assertTrue(result.passed(), result.details());
    }

    @Test
    void staticWebCheckAcceptsBrowserEsModuleJavaScriptFiles() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <script type="module" src="./app.js"></script>
                        </body>
                        </html>
                        """
        );
        Files.writeString(
                tempDir.resolve("app.js"),
                """
                        import { boot } from './boot.js';

                        export function start() {
                          return boot();
                        }

                        start();
                        """
        );
        Files.writeString(
                tempDir.resolve("boot.js"),
                """
                        export function boot() {
                          return 'ok';
                        }
                        """
        );

        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("验证策略规划器")) {
                    return """
                            {
                              "summary": "对静态网页项目执行资源与脚本语法检查。",
                              "steps": [
                                {
                                  "capability": "WEB_RESOURCE_LINK_CHECK",
                                  "reason": "确认页面本地资源完整。",
                                  "required": true
                                },
                                {
                                  "capability": "WEB_JAVASCRIPT_SYNTAX_CHECK",
                                  "reason": "确认浏览器脚本语法可执行。",
                                  "required": true
                                }
                              ]
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(new FileProjectWorkspace(), provider, new com.fasterxml.jackson.databind.ObjectMapper());
        SelfCheckResult result = executor.selfCheck(tempDir);

        assertTrue(result.passed(), result.details());
    }

    @Test
    void selfCheckIgnoresBrokenSecondaryHtmlOutsideResolvedEntry() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <script src="./app.js"></script>
                        </body>
                        </html>
                        """
        );
        Files.writeString(tempDir.resolve("app.js"), "console.log('ok');");
        Files.writeString(
                tempDir.resolve("demo.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <head>
                          <link rel="stylesheet" href="./missing.css">
                        </head>
                        </html>
                        """
        );

        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("验证策略规划器")) {
                    return """
                            {
                              "summary": "对当前网页入口执行资源与脚本语法检查。",
                              "steps": [
                                {
                                  "capability": "WEB_RESOURCE_LINK_CHECK",
                                  "reason": "确认当前入口资源完整。",
                                  "required": true
                                },
                                {
                                  "capability": "WEB_JAVASCRIPT_SYNTAX_CHECK",
                                  "reason": "确认当前入口脚本语法可执行。",
                                  "required": true
                                }
                              ]
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(
                new FileProjectWorkspace(),
                provider,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        SelfCheckResult result = executor.selfCheck(tempDir);

        assertTrue(result.passed(), result.details());
        assertFalse(result.details().contains("missing.css"));
    }

    @Test
    void projectInspectorTreatsSingleHtmlWithInlineScriptAsWebStatic() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <script>
                            console.log('inline');
                          </script>
                        </body>
                        </html>
                        """
        );

        ProjectInspector inspector = new ProjectInspector(new FileProjectWorkspace());
        assertEquals("web-static", inspector.inspect(tempDir).projectType());
        assertEquals("index.html", inspector.inspect(tempDir).resolvedHtmlEntryPath());
    }

    @Test
    void projectInspectorResolvesHtmlEntryWhenIndexIsMissing() throws Exception {
        Files.writeString(
                tempDir.resolve("play.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <main id="app"></main>
                        </body>
                        </html>
                        """
        );

        ProjectInspector inspector = new ProjectInspector(new FileProjectWorkspace());
        assertEquals("play.html", inspector.inspect(tempDir).resolvedHtmlEntryPath());
    }

    @Test
    void unsupportedTestcaseExecutorNoLongerMarksRequiredCasesAsPassed() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# no runnable stack");

        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("验证策略规划器")) {
                    return """
                            {
                              "summary": "无可用自检步骤",
                              "steps": []
                            }
                            """;
                }
                if (systemPrompt.contains("测试用例设计器")) {
                    return """
                            {
                              "summary": "生成一条 required 用例",
                              "cases": [
                                {
                                  "id": "TC-001",
                                  "title": "未实现执行器的用例",
                                  "type": "smoke",
                                  "required": true,
                                  "entry": "",
                                  "preconditions": "",
                                  "expected": "应被标记为未执行",
                                  "steps": [
                                    {
                                      "action": "ASSERT_NO_ERRORS",
                                      "optional": false
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(new FileProjectWorkspace(), provider, new com.fasterxml.jackson.databind.ObjectMapper());
        TestExecutionBundle bundle = executor.execute(tempDir, "goal", "", "", "", "", "");

        assertTrue(bundle.executionMarkdown().contains("未执行"));
        assertTrue(bundle.executionMarkdown().contains("tool=TEST_CASE_EXECUTION"));
        assertTrue(bundle.reportMarkdown().contains("决策：REJECTED"));
    }

    @Test
    void implementationVerificationOutcomeRequestsHumanWhenRuntimeWiringLacksCanonicalRepairPackage() {
        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(
                new FileProjectWorkspace(),
                provider,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        SubtaskVerificationOutcome outcome = executor.toImplementationVerificationOutcome(
                subtask("index.html"),
                new ExperienceFailureDisposition(
                        ExperienceFailureKind.IMPLEMENTATION_CAPABILITY_GAP,
                        "runtime wiring gap",
                        "fix runtime wiring",
                        "missing canonical runtime repair package",
                        ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                        List.of(),
                        List.of("TC-001"),
                        List.of("runtime-wiring"),
                        List.of("runtime-wiring"),
                        ReviewRevisionRoute.PATCH_CURRENT_STAGE,
                        ReviewReasonCode.RUNTIME_WIRING_GAP
                ),
                devflow.agent.i18n.DocumentLanguage.ZH
        );

        assertNotNull(outcome);
        assertEquals(ReviewRevisionRoute.REQUEST_HUMAN, outcome.review().revisionRoute());
        assertEquals(ImplementationPatchTarget.NONE, outcome.review().implementationPatchTarget());
        assertTrue(outcome.review().changeRequest().contains("canonical runtime repair package"));
        assertTrue(outcome.revisionDirective().retryChanges().isEmpty());
    }

    @Test
    void implementationVerificationBlocksForHumanWhenTargetedTestEvidenceIsInvalid() {
        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(new FileProjectWorkspace(), noopProvider(), new com.fasterxml.jackson.databind.ObjectMapper());

        SubtaskVerificationOutcome outcome = executor.toImplementationVerificationOutcome(
                subtask("index.html"),
                new ExperienceFailureDisposition(
                        ExperienceFailureKind.TEST_PLAN_DEFECT,
                        "必测 testcase 结构与能力契约不一致。",
                        "请先修复 TEST 侧必测用例骨架。",
                        "snapshot -> WAIT(policy) -> ASSERT_COMPARE",
                        ImplementationPatchTarget.NONE,
                        List.of(),
                        List.of("TC-001"),
                        List.of("primary-interaction"),
                        List.of("primary-interaction"),
                        ReviewRevisionRoute.PATCH_CURRENT_STAGE,
                        ReviewReasonCode.TEST_PLAN_DEFECT
                ),
                devflow.agent.i18n.DocumentLanguage.ZH
        );

        assertNotNull(outcome);
        assertEquals(ReviewRevisionRoute.REQUEST_HUMAN, outcome.review().revisionRoute());
        assertEquals(ImplementationPatchTarget.NONE, outcome.review().implementationPatchTarget());
        assertTrue(outcome.review().changeRequest().contains("TEST"));
        assertFalse(outcome.revisionDirective().active());
    }

    @Test
    void implementationVerificationKeepsStructuredRepairScopeForImplementationGap() {
        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(new FileProjectWorkspace(), noopProvider(), new com.fasterxml.jackson.databind.ObjectMapper());

        SubtaskVerificationOutcome outcome = executor.toImplementationVerificationOutcome(
                subtask("index.html"),
                new ExperienceFailureDisposition(
                        ExperienceFailureKind.IMPLEMENTATION_CAPABILITY_GAP,
                        "当前实现仍缺少关键体验能力的通过证据。",
                        "请在实现阶段补齐缺失能力。",
                        "missingExperienceCoverage=primary-interaction",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "补齐交互反馈")),
                        List.of("TC-002"),
                        List.of("primary-interaction"),
                        List.of("primary-interaction"),
                        ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET,
                        ReviewReasonCode.IMPLEMENTATION_GAP
                ),
                devflow.agent.i18n.DocumentLanguage.ZH
        );

        assertNotNull(outcome);
        assertEquals(ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET, outcome.review().revisionRoute());
        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION, outcome.review().implementationPatchTarget());
        assertEquals(1, outcome.review().overrideChanges().size());
        assertTrue(outcome.revisionDirective().active());
        assertEquals(1, outcome.revisionDirective().retryChanges().size());
        assertEquals("index.html", outcome.revisionDirective().retryChanges().getFirst().path());
    }

    @Test
    void implementationVerificationRejectsOverrideChangesOutsideCurrentSubtaskScope() {
        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(new FileProjectWorkspace(), noopProvider(), new com.fasterxml.jackson.databind.ObjectMapper());

        SubtaskVerificationOutcome outcome = executor.toImplementationVerificationOutcome(
                subtask("index.html"),
                new ExperienceFailureDisposition(
                        ExperienceFailureKind.IMPLEMENTATION_CAPABILITY_GAP,
                        "当前实现仍缺少关键体验能力的通过证据。",
                        "请在实现阶段补齐缺失能力。",
                        "missingExperienceCoverage=primary-interaction",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        List.of(new FileChange("src/engine.js", ChangeAction.WRITE, "补齐交互反馈")),
                        List.of("TC-002"),
                        List.of("primary-interaction"),
                        List.of("primary-interaction"),
                        ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET,
                        ReviewReasonCode.IMPLEMENTATION_GAP
                ),
                devflow.agent.i18n.DocumentLanguage.ZH
        );

        assertNotNull(outcome);
        assertEquals(ReviewRevisionRoute.REQUEST_HUMAN, outcome.review().revisionRoute());
        assertEquals(ImplementationPatchTarget.NONE, outcome.review().implementationPatchTarget());
        assertTrue(outcome.review().changeRequest().contains("当前子任务负责文件"));
        assertTrue(outcome.revisionDirective().retryChanges().isEmpty());
    }

    @Test
    void targetedReverificationUsesCurrentFailureScopeInsteadOfPreviousFailureScope() {
        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(
                new FileProjectWorkspace(),
                noopProvider(),
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        ReviewResult review = executor.toTargetedReverificationReview(
                new ExperienceFailureDisposition(
                        ExperienceFailureKind.IMPLEMENTATION_CAPABILITY_GAP,
                        "上一轮失败",
                        "修旧 scope",
                        "previous",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        List.of(new FileChange("src/old.js", ChangeAction.WRITE, "旧 scope")),
                        List.of("TC-001"),
                        List.of("primary-interaction"),
                        List.of("primary-interaction"),
                        ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET,
                        ReviewReasonCode.IMPLEMENTATION_GAP
                ),
                new ExperienceFailureDisposition(
                        ExperienceFailureKind.IMPLEMENTATION_CAPABILITY_GAP,
                        "当前失败",
                        "修当前 scope",
                        "current",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "当前 scope")),
                        List.of("TC-002"),
                        List.of("primary-interaction"),
                        List.of("primary-interaction"),
                        ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET,
                        ReviewReasonCode.IMPLEMENTATION_GAP
                ),
                List.of("TC-002"),
                List.of("primary-interaction"),
                devflow.agent.i18n.DocumentLanguage.ZH
        );

        assertNotNull(review);
        assertEquals(ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION, review.implementationPatchTarget());
        assertEquals(1, review.overrideChanges().size());
        assertEquals("index.html", review.overrideChanges().getFirst().path());
    }

    @Test
    void targetedReverificationDoesNotFallbackToPreviousScopeWhenCurrentFailureHasNoCanonicalScope() {
        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(
                new FileProjectWorkspace(),
                noopProvider(),
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        ReviewResult review = executor.toTargetedReverificationReview(
                new ExperienceFailureDisposition(
                        ExperienceFailureKind.IMPLEMENTATION_CAPABILITY_GAP,
                        "上一轮失败",
                        "修旧 scope",
                        "previous",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        List.of(new FileChange("src/old.js", ChangeAction.WRITE, "旧 scope")),
                        List.of("TC-001"),
                        List.of("primary-interaction"),
                        List.of("primary-interaction"),
                        ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET,
                        ReviewReasonCode.IMPLEMENTATION_GAP
                ),
                new ExperienceFailureDisposition(
                        ExperienceFailureKind.IMPLEMENTATION_CAPABILITY_GAP,
                        "当前失败",
                        "当前轮没有 canonical scope",
                        "current",
                        ImplementationPatchTarget.PATCH_EXISTING_IMPLEMENTATION,
                        List.of(),
                        List.of("TC-002"),
                        List.of("primary-interaction"),
                        List.of("primary-interaction"),
                        ReviewRevisionRoute.ROUTE_TO_REPAIR_TARGET,
                        ReviewReasonCode.IMPLEMENTATION_GAP
                ),
                List.of("TC-002"),
                List.of("primary-interaction"),
                devflow.agent.i18n.DocumentLanguage.ZH
        );

        assertNotNull(review);
        assertEquals(ImplementationPatchTarget.NONE, review.implementationPatchTarget());
        assertEquals(ReviewRevisionRoute.REQUEST_HUMAN, review.revisionRoute());
        assertTrue(review.overrideChanges().isEmpty());
        assertTrue(review.changeRequest().contains("文件范围"));
    }

    @Test
    void runnableMilestoneTriggersFunctionalVerificationWithoutDirectHtmlOwnerTouch() {
        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(
                new FileProjectWorkspace(),
                noopProvider(),
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        boolean shouldRun = executor.shouldRunImplementationFunctionalVerification(
                new Subtask(
                        "补运行态",
                        "补齐引擎逻辑",
                        List.of(),
                        List.of("runtime"),
                        List.of(),
                        List.of("里程碑可运行"),
                        true,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("src/engine.js", ChangeAction.WRITE, "补齐引擎逻辑"))
                ),
                milestoneQualityPlan(),
                webFingerprint("index.html", "index.html", "src/engine.js"),
                false
        );

        assertTrue(shouldRun);
    }

    @Test
    void runnableMilestoneScopedPlanKeepsTimedAndObservableCapabilities() {
        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(
                new FileProjectWorkspace(),
                noopProvider(),
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        QualityPlan scoped = executor.scopedImplementationVerificationPlan(
                milestoneQualityPlan(),
                new Subtask(
                        "补运行态",
                        "补齐引擎逻辑",
                        List.of(),
                        List.of("runtime"),
                        List.of(),
                        List.of("里程碑可运行"),
                        true,
                        DeliveryMode.PATCH,
                        List.of(new FileChange("src/engine.js", ChangeAction.WRITE, "补齐引擎逻辑"))
                ),
                false
        );

        List<String> capabilityIds = scoped.capabilityMatrix().entries().stream()
                .map(CapabilityMatrixEntry::capabilityId)
                .toList();

        assertTrue(capabilityIds.contains(CapabilityIds.PAGE_LOAD));
        assertTrue(capabilityIds.contains(CapabilityIds.PRIMARY_INTERACTION));
        assertTrue(capabilityIds.contains(CapabilityIds.TIMED_STATE_PROGRESSION));
        assertFalse(capabilityIds.contains("non-runtime-capability"));
    }

    @Test
    void missingHtmlEntryIsReportedAsExecutionContractFailure() throws Exception {
        Files.writeString(tempDir.resolve("game.js"), "export const boot = () => 'ok';");

        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("验证策略规划器")) {
                    return """
                            {
                              "summary": "仅做脚本自检",
                              "steps": []
                            }
                            """;
                }
                if (systemPrompt.contains("测试用例设计器")) {
                    return """
                            {
                              "summary": "生成一条网页必测用例",
                              "cases": [
                                {
                                  "id": "TC-001",
                                  "title": "网页入口可打开",
                                  "type": "smoke",
                                  "required": true,
                                  "entry": "",
                                  "preconditions": "",
                                  "expected": "页面可打开",
                                  "steps": [
                                    {
                                      "action": "ASSERT_NO_ERRORS",
                                      "optional": false
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(new FileProjectWorkspace(), provider, new com.fasterxml.jackson.databind.ObjectMapper());
        TestExecutionBundle bundle = executor.execute(
                tempDir,
                "实现一个纯网页版俄罗斯方块",
                "需要纯网页版、能直接打开运行",
                """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可直接打开运行的俄罗斯方块网页。

                ## 2. 目标用户与使用场景
                - 用户打开页面后即可开始游戏。

                ## 3. 功能范围
                - 支持开始、暂停、重开。

                ## 4. 非功能要求
                - 纯静态交付。

                ## 5. 验收标准
                - 页面可打开并可开始游戏。

                ## 6. 不做什么
                - 不做联网功能。

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, game-surface-renders
                """,
                """
                # 技术方案设计

                ## 1. 技术目标
                - 以静态网页形式交付可运行的最小游戏表面。

                ## 2. 系统边界与模块划分
                - 页面入口、渲染逻辑和输入逻辑分离。

                ## 3. 核心数据模型
                - 棋盘、方块、得分、运行状态。

                ## 4. 关键流程
                - 页面加载、开始、暂停、重开。

                ## 5. 接口、页面或命令设计
                - 页面必须提供可见游戏区域和启动控件。

                ## 6. 测试与验证策略
                - 通过浏览器 smoke 验证页面可打开和表面存在。

                ## 7. 风险与取舍
                - 先保证页面可运行，再补全逻辑细节。

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, game-surface-renders
                """,
                "# implementation",
                ""
        );

        assertTrue(bundle.executionMarkdown().contains("failureReason: entry-missing"), bundle.executionMarkdown());
        assertTrue(bundle.reportMarkdown().contains("决策：REJECTED"), bundle.reportMarkdown());
    }

    @Test
    void architectRunnableCheckBlocksHalfFinishedWebDeliverableBeforeTestcasesPass() throws Exception {
        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <head>
                          <meta charset="UTF-8">
                          <title>Tetris</title>
                        </head>
                        <body>
                          <main id="app-root">
                            <canvas id="game-canvas"></canvas>
                            <button id="start-btn">开始</button>
                          </main>
                          <script>
                            function update() {
                            }

                            function render() {
                            }

                            document.addEventListener('DOMContentLoaded', () => {
                              const button = document.getElementById('start-btn');
                              if (button) {
                                button.textContent = '开始';
                              }
                            });
                          </script>
                        </body>
                        </html>
                        """
        );

        LlmProvider provider = new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                if (systemPrompt.contains("验证策略规划器")) {
                    return """
                            {
                              "summary": "执行基础静态网页自检。",
                              "steps": [
                                {
                                  "capability": "WEB_RESOURCE_LINK_CHECK",
                                  "reason": "检查入口资源。",
                                  "required": true
                                },
                                {
                                  "capability": "WEB_JAVASCRIPT_SYNTAX_CHECK",
                                  "reason": "检查脚本语法。",
                                  "required": true
                                }
                              ]
                            }
                            """;
                }
                if (systemPrompt.contains("测试用例设计器")) {
                    return """
                            {
                              "summary": "生成一条可交互用例",
                              "cases": [
                                {
                                  "id": "TC-001",
                                  "title": "点击开始按钮后界面变化",
                                  "type": "functional",
                                  "required": true,
                                  "entry": "index.html",
                                  "preconditions": "",
                                  "expected": "点击后画面发生变化",
                                  "steps": [
                                    {
                                      "action": "ASSERT_SELECTOR",
                                      "selector": "#start-btn",
                                      "optional": false
                                    },
                                    {
                                      "action": "CLICK",
                                      "selector": "#start-btn",
                                      "optional": false
                                    }
                                  ]
                                }
                              ]
                            }
                            """;
                }
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };

        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(new FileProjectWorkspace(), provider, new com.fasterxml.jackson.databind.ObjectMapper());
        TestExecutionBundle bundle = executor.execute(
                tempDir,
                "实现一个可直接运行的俄罗斯方块",
                "需要纯网页版、可直接打开运行",
                """
                # 产品需求文档

                ## 1. 产品目标
                - 实现一个可直接打开运行的俄罗斯方块网页。

                ## 2. 目标用户与使用场景
                - 用户打开页面即可开始游戏。

                ## 3. 功能范围
                - 支持开始、暂停、重开和键盘控制。

                ## 4. 非功能要求
                - 纯静态交付。

                ## 5. 验收标准
                - 页面可打开并可开始游戏。

                ## 6. 不做什么
                - 不做联网功能。

                ## 7. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, runtime-surface-renders
                """,
                """
                # 技术方案设计

                ## 1. 技术目标
                - 以静态网页形式交付可运行的最小游戏表面。

                ## 2. 系统边界与模块划分
                - 页面入口、渲染逻辑和输入逻辑分离。

                ## 3. 核心数据模型
                - 棋盘、方块、得分、运行状态。

                ## 4. 关键流程
                - 页面加载、开始、暂停、重开。

                ## 5. 接口、页面或命令设计
                - 页面必须提供可见游戏区域和启动控件。

                ## 6. 测试与验证策略
                - 通过浏览器 smoke 验证页面可打开和表面存在。

                ## 7. 风险与取舍
                - 先保证页面可运行，再补全逻辑细节。

                ## 8. Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry
                - runtime.entryPackagingMode: entry-with-local-dependencies
                - runtime.runtimeOwnershipMode: not-applicable
                - runtime.launchRequired: true
                - runtime.surfaceRequired: true
                - runtime.acceptanceSignals: page-opens, runtime-surface-renders, game-starts
                """,
                "# implementation",
                ""
        );

        assertTrue(bundle.executionMarkdown().contains("Architect Runnable Check")
                || bundle.executionMarkdown().contains("整体可运行检查"));
        assertTrue(bundle.executionMarkdown().contains("implementation-incomplete") || bundle.executionMarkdown().contains("implementation incomplete"));
        assertTrue(bundle.reportMarkdown().contains("架构检查通过：false"), bundle.reportMarkdown());
        assertTrue(bundle.reportMarkdown().contains("决策：REJECTED"), bundle.reportMarkdown());
    }

    @Test
    void selfCheckRefreshesValidationPlanWhenProjectFingerprintChanges() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# bootstrap");

        TestExecutor executor = devflow.agent.executor.testing.TestExecutorTestSupport.create(
                new FileProjectWorkspace(),
                noopProvider(),
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        SelfCheckResult initial = executor.selfCheck(tempDir);
        assertTrue(initial.passed(), initial.details());

        Files.writeString(
                tempDir.resolve("index.html"),
                """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <body>
                          <script src="./missing.js"></script>
                        </body>
                        </html>
                        """
        );

        SelfCheckResult changed = executor.selfCheck(tempDir);

        assertFalse(changed.passed(), changed.details());
    }

    private LlmProvider noopProvider() {
        return new devflow.agent.testsupport.RequestBackedLlmProvider() {
            @Override
            public String generate(String systemPrompt, String userPrompt, Map<String, Object> options) {
                return "";
            }

            @Override
            public ReviewResult review(String systemPrompt, String candidateContent, Map<String, Object> options) {
                return new ReviewResult(ReviewDecision.APPROVED, FixMode.NONE, "ok", "");
            }
        };
    }

    private Subtask subtask(String... paths) {
        return new Subtask(
                "验证子任务",
                "只修当前子任务文件",
                List.of(),
                List.of("current-capability"),
                List.of(),
                List.of("当前子任务通过验证"),
                false,
                DeliveryMode.PATCH,
                java.util.Arrays.stream(paths)
                        .map(path -> new FileChange(path, ChangeAction.WRITE, "当前子任务负责文件"))
                        .toList()
        );
    }

    private QualityPlan milestoneQualityPlan() {
        return new QualityPlan(
                new FeatureProfile(true, true, false, true, true, true, true, true, false),
                QualityIntent.empty(),
                StructureRiskReport.low(),
                new StructurePolicy(true, true, StructureRiskLevel.MEDIUM),
                new CoveragePolicy(3, true, true),
                new ExperiencePolicy(true, true),
                new CapabilityMatrix(List.of(
                        new CapabilityMatrixEntry(CapabilityIds.PAGE_LOAD, CapabilityExpectation.REQUIRED, false, ""),
                        new CapabilityMatrixEntry(CapabilityIds.PRIMARY_VISUAL_SURFACE, CapabilityExpectation.REQUIRED, false, ""),
                        new CapabilityMatrixEntry(CapabilityIds.PRIMARY_INTERACTION, CapabilityExpectation.REQUIRED, true, ""),
                        new CapabilityMatrixEntry(CapabilityIds.TIMED_STATE_PROGRESSION, CapabilityExpectation.REQUIRED, true, ""),
                        new CapabilityMatrixEntry("non-runtime-capability", CapabilityExpectation.REQUIRED, false, "")
                )),
                QualityChecklist.empty()
        );
    }

    private ProjectFingerprint webFingerprint(String htmlEntry, String... fileNames) {
        return new ProjectFingerprint(
                "web-static",
                "none",
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                htmlEntry,
                Set.of(fileNames),
                List.of()
        );
    }
}
