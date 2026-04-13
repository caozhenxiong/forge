package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import devflow.agent.executor.implementation.toolloop.ImplementationDiagnosticRecord;
import devflow.agent.executor.subtask.SubtaskExecutionReport;
/**
 * implementation diagnostics 的统一渲染入口。
 *
 * <p>diagnostics 属于运行态结构证据，不允许继续散落在 mutation prose 里。
 */
final class ImplementationDiagnosticRenderer {

    String renderReportSection(ImplementationRuntimeSnapshot snapshot) {
        ImplementationDiagnosticsPayload payload = buildPayload(snapshot);
        StringBuilder builder = new StringBuilder(
                StructuredArtifactBlocks.renderJsonBlock(ArtifactBlockKind.IMPLEMENTATION_DIAGNOSTICS, payload)
        );
        builder.append("\n\n## ")
                .append(snapshot.language().choose("诊断摘要", "Diagnostic Summary"))
                .append("\n\n");
        if (payload.diagnostics().isEmpty()) {
            builder.append(PlaceholderValues.bulletNone(snapshot.language())).append('\n');
            return builder.toString().trim();
        }
        for (ImplementationDiagnosticsPayload.Entry entry : payload.diagnostics()) {
            builder.append("- [")
                    .append(entry.status())
                    .append("] `")
                    .append(entry.relativePath())
                    .append("`")
                    .append(" @ ")
                    .append(entry.subtaskTitle())
                    .append(" :: ")
                    .append(entry.evidence())
                    .append('\n');
        }
        return builder.toString().trim();
    }

    String renderArtifact(ImplementationRuntimeSnapshot snapshot) {
        DocumentLanguage language = snapshot.language();
        ImplementationDiagnosticsPayload payload = buildPayload(snapshot);
        StringBuilder builder = new StringBuilder("# ")
                .append(language.choose("实现诊断", "Implementation Diagnostics"))
                .append("\n\n");
        builder.append(StructuredArtifactBlocks.renderJsonBlock(ArtifactBlockKind.IMPLEMENTATION_DIAGNOSTICS, payload))
                .append("\n\n");
        if (payload.diagnostics().isEmpty()) {
            builder.append(PlaceholderValues.bulletNone(language)).append('\n');
            return builder.toString().trim();
        }
        for (ImplementationDiagnosticsPayload.Entry entry : payload.diagnostics()) {
            builder.append("## ").append(entry.subtaskTitle()).append(" / `").append(entry.relativePath()).append("`\n\n");
            builder.append("- diagnosticId: ").append(entry.diagnosticId()).append('\n');
            builder.append("- status: ").append(entry.status()).append('\n');
            builder.append("- source: ").append(entry.source()).append('\n');
            builder.append("- timestamp: ").append(entry.timestamp()).append('\n');
            builder.append("- evidence: ").append(entry.evidence()).append("\n\n");
        }
        return builder.toString().trim();
    }

    private ImplementationDiagnosticsPayload buildPayload(ImplementationRuntimeSnapshot snapshot) {
        if (snapshot == null || snapshot.reports() == null || snapshot.reports().isEmpty()) {
            return new ImplementationDiagnosticsPayload(List.of());
        }
        List<ImplementationDiagnosticsPayload.Entry> diagnostics = new ArrayList<>();
        for (SubtaskExecutionReport report : snapshot.reports()) {
            if (report == null || report.executionState() == null || report.executionState().toolSessionState() == null) {
                continue;
            }
            String subtaskTitle = report.subtask() == null ? "" : report.subtask().title();
            for (ImplementationDiagnosticRecord diagnostic : report.executionState().toolSessionState().diagnostics()) {
                if (diagnostic == null || diagnostic.relativePath() == null) {
                    continue;
                }
                diagnostics.add(new ImplementationDiagnosticsPayload.Entry(
                        subtaskTitle,
                        diagnostic.diagnosticId(),
                        diagnostic.relativePath().toString(),
                        diagnostic.status().name(),
                        diagnostic.source().name(),
                        diagnostic.evidence(),
                        diagnostic.timestamp()
                ));
            }
        }
        diagnostics.sort(Comparator.comparingLong(ImplementationDiagnosticsPayload.Entry::timestamp));
        return new ImplementationDiagnosticsPayload(diagnostics);
    }
}
