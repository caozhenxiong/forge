package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.toolloop.*;

import devflow.agent.context.ContractView;
import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.quality.QualityPlan;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import devflow.agent.executor.implementation.toolloop.ImplementationToolPromptBuilder;
import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.TaskPackage;
import devflow.agent.executor.subtask.ExecutionFileContract;
import devflow.agent.executor.subtask.ExecutionFileContractMode;
import devflow.agent.executor.subtask.ExecutionFileContractSet;
class ImplementationToolPromptBuilderTests {

    private final ImplementationToolPromptBuilder builder = new ImplementationToolPromptBuilder();

    @Test
    void rendersRuntimeWiringFileContractsForCurrentSubtask() {
        Subtask subtask = new Subtask(
                "修接线",
                "只修宿主接线",
                List.of(),
                List.of(),
                List.of(),
                List.of("入口接回 companion runtime"),
                true,
                DeliveryMode.PATCH,
                List.of(
                        new FileChange(
                                "index.html",
                                ChangeAction.WRITE,
                                "修接线",
                                FileEditScope.HOST_HTML_PATCH,
                                RuntimeOwnershipMode.EXTERNAL_COMPANION,
                                true
                        ),
                        new FileChange("src/app.js", ChangeAction.WRITE, "保留 companion runtime")
                )
        );
        TaskPackage taskPackage = new TaskPackage(
                "修接线",
                "只修宿主接线",
                DeliveryMode.PATCH.name(),
                true,
                List.of("index.html", "src/app.js"),
                List.of(),
                List.of(),
                List.of(),
                List.of("入口接回 companion runtime"),
                List.of(),
                List.of(),
                "",
                new ExecutionFileContractSet(List.of(
                        new ExecutionFileContract(
                                Path.of("index.html"),
                                ExecutionFileContractMode.PATCH_EXISTING,
                                new FileChange(
                                        "index.html",
                                        ChangeAction.WRITE,
                                        "修接线",
                                        FileEditScope.HOST_HTML_PATCH,
                                        RuntimeOwnershipMode.EXTERNAL_COMPANION,
                                        true
                                )
                        ),
                        new ExecutionFileContract(
                                Path.of("src/app.js"),
                                ExecutionFileContractMode.CREATE_NEW,
                                new FileChange("src/app.js", ChangeAction.WRITE, "保留 companion runtime")
                        )
                )),
                null
        );

        String prompt = builder.userPrompt(
                Path.of("/tmp/project"),
                subtask,
                taskPackage,
                new ContractView(null, null, null, ConstraintSourceMetadata.empty()),
                QualityPlan.empty(),
                "",
                ""
        );

        assertTrue(prompt.contains("# Current File Contracts"));
        assertTrue(prompt.contains("runtimeOwnership=EXTERNAL_COMPANION"));
        assertTrue(prompt.contains("declaredRuntimeRoots=src/app.js"));
        assertTrue(prompt.contains("constraint=仅修宿主 HTML 结构与接线，不要把主运行时代码内联回宿主页面"));
    }

    @Test
    void taskPackageAlignsOwnedFilesToEffectiveSubtaskScope() {
        TaskPackage original = new TaskPackage(
                "创建入口页面与基础结构",
                "原始子任务",
                DeliveryMode.INCREMENTAL.name(),
                true,
                List.of("index.html", "src/app.js"),
                List.of("CAP-1"),
                List.of("SHELL"),
                List.of("GAMEPLAY"),
                List.of("页面可以打开"),
                List.of(),
                List.of(),
                "",
                ExecutionFileContractSet.empty(),
                null
        );
        Subtask effectiveSubtask = new Subtask(
                "创建入口页面与基础结构",
                "当前只修接线",
                List.of("CAP-1"),
                List.of(),
                List.of("GAMEPLAY"),
                List.of("页面可以打开"),
                true,
                DeliveryMode.PATCH,
                List.of(new FileChange(
                        "index.html",
                        ChangeAction.WRITE,
                        "只修宿主接线",
                        FileEditScope.HOST_HTML_PATCH,
                        RuntimeOwnershipMode.EXTERNAL_COMPANION,
                        true
                ))
        );

        TaskPackage aligned = original.alignToSubtask(effectiveSubtask);

        String markdown = aligned.toMarkdown(devflow.agent.i18n.DocumentLanguage.ZH);
        assertTrue(markdown.contains("- index.html"));
        assertTrue(!markdown.contains("- src/app.js"));
        assertTrue(markdown.contains("- 交付模式: PATCH"));
        assertTrue(markdown.contains("### 当前负责能力"));
        assertTrue(markdown.contains("- (无)"));
        assertTrue(markdown.contains("### 后续负责能力"));
        assertTrue(markdown.contains("- GAMEPLAY"));
        assertTrue(!markdown.contains("- SHELL"));
    }

    @Test
    void rendersContinuationPromptFromBuilder() {
        String prompt = builder.continuationPrompt("只修复当前反馈，不要重开子任务。");

        assertTrue(prompt.contains("继续当前子任务，不要重新开始整个实现。"));
        assertTrue(prompt.contains("最新反馈："));
        assertTrue(prompt.contains("只修复当前反馈，不要重开子任务。"));
    }
}
