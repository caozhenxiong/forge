package devflow.agent.executor.subtask;

import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.DeliveryMode;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.SelfCheckResult;
import devflow.agent.executor.gate.ImplementationCompletenessResult;
import devflow.agent.quality.QualityPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtaskReviewPromptAssemblerTests {

    @Test
    void candidatePromptIncludesBoundaryContractAndOwnedFiles() {
        SubtaskReviewPromptAssembler assembler = new SubtaskReviewPromptAssembler();

        String prompt = assembler.candidatePrompt(
                new Subtask(
                        "搭壳体",
                        "只做页面壳体",
                        List.of("CAP-1"),
                        List.of("页面壳体"),
                        List.of("gameplay"),
                        List.of("页面可打开"),
                        true,
                        DeliveryMode.SKELETON,
                        List.of(new FileChange("index.html", ChangeAction.WRITE, "创建入口"))
                ),
                new SelfCheckResult(true, "ok", "details"),
                ImplementationCompletenessResult.success(),
                QualityPlan.empty(),
                "targeted context",
                "performance guidance",
                ""
        );

        assertTrue(prompt.contains("当前负责文件"));
        assertTrue(prompt.contains("index.html"));
        assertTrue(prompt.contains("边界契约提醒"));
        assertTrue(prompt.contains("offendingPaths 必须来自当前负责文件"));
        assertTrue(prompt.contains("deferredCapabilities"));
    }
}
