package devflow.agent.orchestrator;

import devflow.agent.review.FixMode;
import org.springframework.stereotype.Component;

/**
 * 统一维护阶段推进与回退规则。
 *
 * <p>这些规则本质上是确定性的流程策略，不应该散落在
 * WorkflowEngine、Supervisor 或其他执行组件里各自维护一份。
 */
@Component
public class StageFlowPolicy {

    /**
     * 返回当前阶段在正常推进时的下一阶段；测试阶段之后不再有后继阶段。
     */
    public StageType nextStage(StageType stageType) {
        if (stageType == StageType.ANALYSIS) {
            return StageType.PRD;
        }
        if (stageType == StageType.PRD) {
            return StageType.DESIGN;
        }
        if (stageType == StageType.DESIGN) {
            return StageType.IMPLEMENTATION;
        }
        if (stageType == StageType.IMPLEMENTATION) {
            return StageType.CODE_REVIEW;
        }
        if (stageType == StageType.CODE_REVIEW) {
            return StageType.TEST;
        }
        return null;
    }

    /**
     * 返回当前阶段在需要修订时应回退到的阶段。
     *
     * <p>目前规则是：文档阶段原地修订，实现/评审/测试统一回到 IMPLEMENTATION。
     * 这里保留 fixMode 参数，是为了后续扩展更细的回退策略，而不是让各处再额外分叉。
     */
    public StageType rerouteStage(StageType stageType, FixMode fixMode) {
        if (stageType == StageType.ANALYSIS
                || stageType == StageType.PRD
                || stageType == StageType.DESIGN) {
            return stageType;
        }
        return StageType.IMPLEMENTATION;
    }
}
