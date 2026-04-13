package devflow.agent.executor.gate;

import devflow.agent.executor.*;
import devflow.agent.executor.runtime.*;

/**
 * 所有确定性 gate 的最小统一接口。
 *
 * <p>确定性 gate 不调用模型，也不做主观判断；输入相同，输出必须相同。
 */
public interface DeterministicGate<T> {

    GateReport evaluate(T input);
}
