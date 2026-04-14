package devflow.agent.executor.implementation.render;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.planning.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.context.SharedContextBundle;
import devflow.agent.i18n.DocumentLanguage;
import java.util.List;

import devflow.agent.executor.subtask.SubtaskExecutionReport;
import devflow.agent.executor.subtask.TaskPackage;
/**
 * 负责 implementation 阶段辅助产物的渲染。
 * 这个类只负责把结构化状态转成 markdown/json，不参与规划、生成、校验或阶段 gate。
 */
public class ImplementationArtifactRenderer {

    private final ImplementationReportRenderer reportRenderer;
    private final ImplementationWorkArtifactRenderer workArtifactRenderer;
    private final ImplementationRuntimeArtifactRenderer runtimeArtifactRenderer;

    public ImplementationArtifactRenderer(ObjectMapper objectMapper) {
        this.reportRenderer = new ImplementationReportRenderer();
        this.workArtifactRenderer = new ImplementationWorkArtifactRenderer();
        this.runtimeArtifactRenderer = new ImplementationRuntimeArtifactRenderer(objectMapper);
    }

    public String renderReport(ImplementationRuntimeSnapshot snapshot) {
        return reportRenderer.renderReport(snapshot);
    }

    public String renderBacklog(
            ImplementationPlan plan,
            DeliveryPolicyEnvelope deliveryPolicy,
            SharedContextBundle sharedContextBundle,
            DocumentLanguage language
    ) {
        return workArtifactRenderer.renderBacklog(plan, deliveryPolicy, sharedContextBundle, language);
    }

    public String renderTaskPackages(List<TaskPackage> taskPackages, DocumentLanguage language) {
        return workArtifactRenderer.renderTaskPackages(taskPackages, language);
    }

    public String renderWorkerResults(ImplementationRuntimeSnapshot snapshot) {
        return workArtifactRenderer.renderWorkerResults(snapshot);
    }

    public String renderEvents(ImplementationRuntimeSnapshot snapshot) {
        return runtimeArtifactRenderer.renderEvents(snapshot);
    }

    public String renderDiagnostics(ImplementationRuntimeSnapshot snapshot) {
        return runtimeArtifactRenderer.renderDiagnostics(snapshot);
    }

    public String renderRepairAlignment(
            List<SubtaskExecutionReport> reports,
            String note,
            DeliveryPolicyEnvelope deliveryPolicy,
            DocumentLanguage language
    ) {
        return reportRenderer.renderRepairAlignment(reports, note, deliveryPolicy, language);
    }

    public String renderStateJson(ImplementationRuntimeSnapshot runtimeSnapshot) {
        return runtimeArtifactRenderer.renderStateJson(runtimeSnapshot);
    }

    public String renderStageStatus(ImplementationRuntimeSnapshot runtimeSnapshot) {
        return runtimeArtifactRenderer.renderStageStatus(runtimeSnapshot);
    }

    /**
     * 运行中进度文件用于实时观察 implementation 的推进情况。
     * 这里故意把“计划总览”和“当前执行点”放在同一份文件里，方便排查长时间运行时到底卡在哪一步。
     */
    public String renderProgress(ImplementationRuntimeSnapshot runtimeSnapshot) {
        return runtimeArtifactRenderer.renderProgress(runtimeSnapshot);
    }
}
