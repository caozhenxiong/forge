package devflow.agent.executor.generation;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * 把 generation telemetry 渲染成适合 events.log 的中文内联片段。
 */
public final class GenerationTelemetryFormatter {

    private GenerationTelemetryFormatter() {
    }

    public static String renderInline(GenerationTelemetry telemetry) {
        if (telemetry == null) {
            return "";
        }
        return "｜输入token=估算%s/实际%s｜fixed=%s｜retrieved=%s｜output-reserve=%s｜material-budget=%s｜输出token=%s｜上下文token=%s｜预留token=%s｜可用输出token=%s｜请求输出token=%s｜生效输出token=%s｜结束原因=%s"
                .formatted(
                        renderNumber(telemetry.estimatedPromptTokens()),
                        renderNumber(telemetry.actualPromptTokens()),
                        renderNumber(telemetry.fixedTokens()),
                        renderNumber(telemetry.retrievedTokens()),
                        renderNumber(telemetry.outputReserveTokens()),
                        renderNumber(telemetry.materialBudgetTokens()),
                        renderNumber(telemetry.outputTokens()),
                        renderNumber(telemetry.contextWindowTokens()),
                        renderNumber(telemetry.reserveTokens()),
                        renderNumber(telemetry.availableOutputTokens()),
                        renderNumber(telemetry.requestedOutputTokens()),
                        renderNumber(telemetry.effectiveOutputTokens()),
                        renderText(telemetry.doneReason())
                );
    }

    private static String renderNumber(Integer value) {
        return value == null ? "未知" : String.valueOf(value);
    }

    private static String renderText(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }
}
