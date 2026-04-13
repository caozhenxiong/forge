package devflow.agent.domain;

import java.util.EnumMap;
import java.util.Map;

public record RunConfig(
        Map<StageType, GatePolicy> gatePolicies,
        int maxAutoRevisions
) {

    public static RunConfig defaultConfig() {
        EnumMap<StageType, GatePolicy> gatePolicies = new EnumMap<>(StageType.class);
        gatePolicies.put(StageType.ANALYSIS, GatePolicy.AGENT_PLUS_HUMAN);
        gatePolicies.put(StageType.PRD, GatePolicy.AGENT_PLUS_HUMAN);
        gatePolicies.put(StageType.DESIGN, GatePolicy.AGENT_PLUS_HUMAN);
        gatePolicies.put(StageType.IMPLEMENTATION, GatePolicy.AGENT_ONLY);
        gatePolicies.put(StageType.CODE_REVIEW, GatePolicy.AGENT_PLUS_HUMAN);
        gatePolicies.put(StageType.TEST, GatePolicy.AGENT_ONLY);
        return new RunConfig(gatePolicies, 5);
    }
}
