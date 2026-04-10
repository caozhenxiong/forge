package devflow.agent.executor;

import java.util.List;

/**
 * 代码行为扫描的中间结果。
 * 当前只携带发现列表，后续若要增加语言级统计可以继续扩展。
 */
record ImplementationBehaviorInspection(
        List<ImplementationCompletenessFinding> findings
) {
}
