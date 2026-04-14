package devflow.agent.executor.implementation.planning;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.util.List;

/**
 * outline 层的单个子任务声明。
 *
 * <p>这里用 targetPaths 预先固定当前子任务允许触达的文件范围，后续 detail 只能在这组路径内细化变更。
 * 同一路径可以被多个顺序子任务继续修改，但 router 会按最后命中的子任务做确定性回路。
 */
public record ImplementationOutlineSubtask(
        String id,
        String title,
        String goal,
        List<String> coverageRefs,
        List<String> ownedCapabilities,
        List<String> deferredCapabilities,
        List<String> acceptanceCriteria,
        boolean runnableMilestone,
        DeliveryMode deliveryMode,
        List<String> targetPaths
) {
}
