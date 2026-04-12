package devflow.agent.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import devflow.agent.executor.PlaywrightProbeRunner;
import devflow.agent.executor.RuntimeSnapshot;
import devflow.agent.executor.RuntimeSnapshotCaptureStatus;
import devflow.agent.executor.ToolFailureCode;
import devflow.agent.executor.ToolName;
import devflow.agent.executor.ToolResult;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.project.FileProjectWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 浏览器级 smoke validation 支撑。
 */
final class PlaywrightSmokeValidationSupport {

    private final PlaywrightProbeRunner probeRunner;

    PlaywrightSmokeValidationSupport(FileProjectWorkspace workspace) {
        this.probeRunner = new PlaywrightProbeRunner(workspace, new ObjectMapper());
    }

    ValidationStepExecution run(Path projectPath, ProjectFingerprint fingerprint, String reason) {
        String entry = fingerprint == null ? "" : fingerprint.resolvedHtmlEntryPath();
        if (entry.isBlank()) {
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                            ValidationStatus.SKIPPED,
                            "未探测到 HTML 入口，跳过浏览器 smoke test。",
                            reason
                    ),
                    ToolResult.skipped(
                            ToolName.PLAYWRIGHT_SMOKE,
                            "未探测到 HTML 入口。",
                            "如需浏览器级验证，请先提供有效的 HTML 入口。"
                    )
            );
        }
        Path entryPath = projectPath.resolve(entry);
        if (!Files.exists(entryPath)) {
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                            ValidationStatus.SKIPPED,
                            "探测到的 HTML 入口不存在，跳过浏览器 smoke test。",
                            reason + "\nentry=" + entry
                    ),
                    ToolResult.skipped(
                            ToolName.PLAYWRIGHT_SMOKE,
                            "入口文件不存在: " + entry,
                            "请先修复入口文件路径，再继续浏览器级验证。"
                    )
            );
        }
        PlaywrightProbeRunner.ProbeOutcome probeOutcome = probeRunner.probe(projectPath, entry);
        RuntimeSnapshot snapshot = probeOutcome.runtimeSnapshot();
        if (snapshot == null || snapshot.captureStatus() != RuntimeSnapshotCaptureStatus.CAPTURED) {
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                            ValidationStatus.FAILED,
                            "浏览器级 smoke test 失败。",
                            reason + "\n" + renderProbeEvidence(snapshot, probeOutcome.evidence())
                    ),
                    ToolResult.failure(
                            ToolName.PLAYWRIGHT_SMOKE,
                            failureCode(snapshot),
                            renderProbeEvidence(snapshot, probeOutcome.evidence()),
                            "请先修复浏览器级运行失败，再继续依赖 smoke test 结论。"
                    )
            );
        }
        if (hasRuntimeErrors(snapshot)) {
            String runtimeEvidence = renderProbeEvidence(snapshot, probeOutcome.evidence());
            String firstRuntimeError = firstRuntimeError(snapshot);
            return new ValidationStepExecution(
                    new ValidationStepResult(
                            ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                            ValidationStatus.FAILED,
                            "浏览器级 smoke test 发现运行时错误。",
                            reason + "\n" + runtimeEvidence
                    ),
                    ToolResult.failure(
                            ToolName.PLAYWRIGHT_SMOKE,
                            ToolFailureCode.RUNTIME_SNAPSHOT_CAPTURE_FAILED,
                            firstRuntimeError.isBlank() ? runtimeEvidence : firstRuntimeError,
                            "请先修复页面运行时错误，再继续依赖 smoke 通过结论。"
                    )
            );
        }
        return new ValidationStepExecution(
                new ValidationStepResult(
                        ValidationCapability.WEB_PLAYWRIGHT_SMOKE,
                        ValidationStatus.PASSED,
                        "浏览器级 smoke test 通过。",
                        reason + "\n" + renderProbeEvidence(snapshot, probeOutcome.evidence())
                ),
                ToolResult.success(ToolName.PLAYWRIGHT_SMOKE)
        );
    }

    private String renderProbeEvidence(RuntimeSnapshot snapshot, String rawEvidence) {
        if (snapshot == null) {
            return trim(rawEvidence);
        }
        if (snapshot.probeCaptured()) {
            return """
                    {
                      "entry": "%s",
                      "pageTitle": "%s",
                      "canvasCount": %d,
                      "selectors": %d,
                      "consoleErrors": %d,
                      "pageErrors": %d
                    }
                    """.formatted(
                    snapshot.entry(),
                    snapshot.pageTitle(),
                    snapshot.canvasCount(),
                    snapshot.selectors() == null ? 0 : snapshot.selectors().size(),
                    snapshot.consoleErrors() == null ? 0 : snapshot.consoleErrors().size(),
                    snapshot.pageErrors() == null ? 0 : snapshot.pageErrors().size()
            ).trim();
        }
        if (snapshot.captureErrors() != null && !snapshot.captureErrors().isEmpty()) {
            return String.join(" | ", snapshot.captureErrors());
        }
        return trim(rawEvidence);
    }

    private ToolFailureCode failureCode(RuntimeSnapshot snapshot) {
        if (snapshot != null && snapshot.captureStatus() == RuntimeSnapshotCaptureStatus.PAYLOAD_INVALID) {
            return ToolFailureCode.PLAYWRIGHT_PROBE_PAYLOAD_INVALID;
        }
        return ToolFailureCode.PLAYWRIGHT_PROBE_EXECUTION_FAILED;
    }

    private boolean hasRuntimeErrors(RuntimeSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return (snapshot.consoleErrors() != null && !snapshot.consoleErrors().isEmpty())
                || (snapshot.pageErrors() != null && !snapshot.pageErrors().isEmpty());
    }

    private String firstRuntimeError(RuntimeSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        if (snapshot.pageErrors() != null) {
            for (String error : snapshot.pageErrors()) {
                if (error != null && !error.isBlank()) {
                    return error.trim();
                }
            }
        }
        if (snapshot.consoleErrors() != null) {
            for (String error : snapshot.consoleErrors()) {
                if (error != null && !error.isBlank()) {
                    return error.trim();
                }
            }
        }
        return "";
    }

    private String trim(String value) {
        return PlaceholderValues.truncateTail(value, 12000);
    }
}
