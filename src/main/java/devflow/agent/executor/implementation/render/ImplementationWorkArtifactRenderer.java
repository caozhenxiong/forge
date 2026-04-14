package devflow.agent.executor.implementation.render;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.List;

import devflow.agent.executor.subtask.Subtask;
import devflow.agent.executor.subtask.TaskPackage;
/**
 * 负责 backlog、task package、worker result 这些“工作台视图”产物的渲染。
 * 这些产物都从运行时快照派生，不参与执行状态计算。
 */
final class ImplementationWorkArtifactRenderer {

    String renderBacklog(
            ImplementationPlan plan,
            DeliveryPolicyEnvelope deliveryPolicy,
            SharedContextBundle sharedContextBundle,
            DocumentLanguage language
    ) {
        StringBuilder builder = new StringBuilder("""
                # %s

                - recommendedMode: %s
                - maxFiles: %d
                - maxSymbols: %d
                - preferPreciseEditing: %s
                - forceBacklogSplit: %s
                - requireVerificationBeforeReview: %s

                ## %s

                %s

                ## %s

                """.formatted(
                language.choose("实现待办", "Implementation Backlog"),
                deliveryPolicy.mode(),
                deliveryPolicy.maxFiles(),
                deliveryPolicy.maxSymbols(),
                deliveryPolicy.preferPreciseEditing(),
                deliveryPolicy.forceBacklogSplit(),
                deliveryPolicy.requireVerificationBeforeReview(),
                language.choose("架构共享上下文", "Architect Shared Context"),
                sharedContextBundle == null ? PlaceholderValues.none(language) : sharedContextBundle.toMarkdown(language),
                language.choose("待办项", "Backlog Items")
        ));
        int index = 1;
        for (Subtask subtask : plan.subtasks()) {
            builder.append("### ").append(index++).append(". ").append(subtask.title()).append("\n\n");
            builder.append("- ").append(language.choose("目标", "goal")).append(": ").append(subtask.goal()).append("\n");
            builder.append("- ").append(language.choose("交付模式", "deliveryMode")).append(": ").append(subtask.deliveryMode()).append("\n");
            builder.append("- ").append(language.choose("可运行里程碑", "runnableMilestone")).append(": ").append(subtask.runnableMilestone()).append("\n");
            builder.append("- ").append(language.choose("覆盖引用", "coverageRefs")).append(": ").append(String.join(language.choose("；", "; "), ImplementationArtifactRenderSupport.safeList(subtask.coverageRefs()))).append("\n");
            builder.append("- ").append(language.choose("当前负责能力", "ownedCapabilities")).append(": ").append(String.join(language.choose("；", "; "), ImplementationArtifactRenderSupport.safeList(subtask.ownedCapabilities()))).append("\n");
            builder.append("- ").append(language.choose("后续负责能力", "deferredCapabilities")).append(": ").append(String.join(language.choose("；", "; "), ImplementationArtifactRenderSupport.safeList(subtask.deferredCapabilities()))).append("\n");
            builder.append("- ").append(language.choose("文件", "files")).append(": ").append(ImplementationArtifactRenderSupport.renderChangeList(subtask.changes())).append("\n");
            builder.append("- ").append(language.choose("验收", "acceptance")).append(": ").append(String.join(language.choose("；", "; "), ImplementationArtifactRenderSupport.safeList(subtask.acceptanceCriteria()))).append("\n\n");
        }
        return builder.toString().trim();
    }

    String renderTaskPackages(List<TaskPackage> taskPackages, DocumentLanguage language) {
        StringBuilder builder = new StringBuilder("# " + language.choose("任务包", "Task Packages") + "\n\n");
        if (taskPackages == null || taskPackages.isEmpty()) {
            builder.append(PlaceholderValues.bulletNone(language)).append('\n');
            return builder.toString().trim();
        }
        for (TaskPackage taskPackage : taskPackages) {
            builder.append(taskPackage.toMarkdown(language)).append("\n\n");
        }
        return builder.toString().trim();
    }

    String renderWorkerResults(ImplementationRuntimeSnapshot snapshot) {
        List<WorkerResult> workerResults = snapshot.workerResults();
        DocumentLanguage language = snapshot.language();
        StringBuilder builder = new StringBuilder("# " + language.choose("执行结果", "Worker Results") + "\n\n");
        if (workerResults == null || workerResults.isEmpty()) {
            builder.append(PlaceholderValues.bulletNone(language)).append('\n');
            return builder.toString().trim();
        }
        for (WorkerResult workerResult : workerResults) {
            builder.append(workerResult.toMarkdown(language)).append("\n\n");
        }
        return builder.toString().trim();
    }
}
