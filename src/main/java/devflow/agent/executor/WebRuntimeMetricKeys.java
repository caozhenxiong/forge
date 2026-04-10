package devflow.agent.executor;

/**
 * 集中定义前端运行时测量信号的公开 key。
 * 测试规划、提示词和运行时校验都应复用这里，避免把对象路径和指标名散落硬写。
 */
public final class WebRuntimeMetricKeys {

    public static final String PUBLIC_METRICS_OBJECT = "window.__devflowMetrics";
    public static final String LAST_ACTION_MS = "lastActionMs";

    private WebRuntimeMetricKeys() {
    }

    public static String metricPath(String metricKey) {
        return PUBLIC_METRICS_OBJECT + "." + metricKey;
    }
}
