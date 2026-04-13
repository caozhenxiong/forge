package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.DocumentLanguage;

import devflow.agent.executor.subtask.Subtask;
/**
 * 负责 implementation 主报告里的子任务拆解 section。
 *
 * <p>这层只展开 plan 中的静态子任务描述，不处理执行结果。
 */
final class ImplementationSubtaskBreakdownRenderer {

    String render(ImplementationPlan plan, DocumentLanguage language) {
        StringBuilder builder = new StringBuilder();
        builder.append("## ").append(language.choose("子任务拆解", "Subtask Breakdown")).append("\n\n");
        if (plan.subtasks().isEmpty()) {
            builder.append(language.choose("- 无子任务\n", "- No subtasks\n"));
            return builder.toString();
        }
        for (int index = 0; index < plan.subtasks().size(); index++) {
            Subtask subtask = plan.subtasks().get(index);
            builder.append("### ").append(index + 1).append(". ").append(subtask.title()).append("\n\n");
            builder.append("- ").append(language.choose("目标", "Goal")).append("：").append(subtask.goal()).append('\n');
            builder.append("- ").append(language.choose("交付模式", "Delivery Mode")).append("：").append(subtask.deliveryMode()).append('\n');
            builder.append("- ").append(language.choose("可运行里程碑", "Runnable Milestone")).append("：").append(subtask.runnableMilestone()).append('\n');
            builder.append("- ").append(language.choose("覆盖引用", "Coverage Refs")).append("：")
                    .append(String.join(language.choose("；", "; "), ImplementationArtifactRenderSupport.safeList(subtask.coverageRefs()))).append('\n');
            builder.append("- ").append(language.choose("当前负责能力", "Owned Capabilities")).append("：")
                    .append(String.join(language.choose("；", "; "), ImplementationArtifactRenderSupport.safeList(subtask.ownedCapabilities()))).append('\n');
            builder.append("- ").append(language.choose("后续负责能力", "Deferred Capabilities")).append("：")
                    .append(String.join(language.choose("；", "; "), ImplementationArtifactRenderSupport.safeList(subtask.deferredCapabilities()))).append('\n');
            builder.append("- ").append(language.choose("验收标准", "Acceptance")).append("：")
                    .append(String.join(language.choose("；", "; "), ImplementationArtifactRenderSupport.safeList(subtask.acceptanceCriteria()))).append('\n');
            builder.append("- ").append(language.choose("涉及文件", "Files")).append("：")
                    .append(ImplementationArtifactRenderSupport.renderChangeList(subtask.changes())).append("\n\n");
        }
        return builder.toString();
    }
}
