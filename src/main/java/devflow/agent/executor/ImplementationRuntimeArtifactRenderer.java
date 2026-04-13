package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;

import devflow.agent.executor.implementation.ImplementationEventEntry;
/**
 * 负责 implementation 运行态 artifact 渲染。
 * 例如 events/progress/stateJson 都属于运行时观察视图，应与报告型文档分离。
 */
final class ImplementationRuntimeArtifactRenderer {

    private final ImplementationStateSnapshotSerializer stateSnapshotSerializer;
    private final ImplementationStageStatusArtifactRenderer stageStatusArtifactRenderer;
    private final ImplementationProgressRenderer progressRenderer;
    private final ImplementationDiagnosticRenderer diagnosticRenderer;

    ImplementationRuntimeArtifactRenderer(ObjectMapper objectMapper) {
        this.stateSnapshotSerializer = new ImplementationStateSnapshotSerializer(objectMapper);
        this.stageStatusArtifactRenderer = new ImplementationStageStatusArtifactRenderer();
        this.progressRenderer = new ImplementationProgressRenderer();
        this.diagnosticRenderer = new ImplementationDiagnosticRenderer();
    }

    String renderEvents(ImplementationRuntimeSnapshot snapshot) {
        DocumentLanguage language = snapshot.language();
        StringBuilder builder = new StringBuilder("# " + language.choose("实现事件", "Implementation Events") + "\n\n");
        if (snapshot.events() == null || snapshot.events().isEmpty()) {
            builder.append(PlaceholderValues.bulletNone(language)).append('\n');
            return builder.toString().trim();
        }
        for (ImplementationEventEntry event : snapshot.events()) {
            builder.append("- ")
                    .append(event.timestamp())
                    .append(" ")
                    .append(event.message())
                    .append('\n');
        }
        return builder.toString().trim();
    }

    String renderStateJson(ImplementationRuntimeSnapshot runtimeSnapshot) {
        return stateSnapshotSerializer.renderStateJson(runtimeSnapshot);
    }

    String renderStageStatus(ImplementationRuntimeSnapshot runtimeSnapshot) {
        return stageStatusArtifactRenderer.renderBlock(runtimeSnapshot == null ? null : runtimeSnapshot.stageStatus());
    }

    String renderDiagnostics(ImplementationRuntimeSnapshot runtimeSnapshot) {
        return diagnosticRenderer.renderArtifact(runtimeSnapshot);
    }

    /**
     * 运行中进度文件用于实时观察 implementation 的推进情况。
     * 这里故意把“计划总览”和“当前执行点”放在同一份文件里，方便排查长时间运行时到底卡在哪一步。
     */
    String renderProgress(ImplementationRuntimeSnapshot runtimeSnapshot) {
        return progressRenderer.renderProgress(runtimeSnapshot);
    }
}
