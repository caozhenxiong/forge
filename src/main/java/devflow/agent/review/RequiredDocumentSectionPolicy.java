package devflow.agent.review;

import devflow.agent.orchestrator.StageType;
import java.util.List;

/**
 * 负责文档阶段的必需章节规则。
 *
 * <p>这层只维护确定性结构要求：
 * 1. 各阶段要求哪些二级章节；
 * 2. 哪一章属于 Contract Metadata。
 */
final class RequiredDocumentSectionPolicy {

    List<Integer> requiredSectionNumbers(StageType stageType) {
        if (stageType == StageType.ANALYSIS) {
            return List.of(1, 2, 3, 4, 5, 6);
        }
        if (stageType == StageType.PRD) {
            return List.of(1, 2, 3, 4, 5, 6, 7);
        }
        if (stageType == StageType.DESIGN) {
            return List.of(1, 2, 3, 4, 5, 6, 7, 8);
        }
        return List.of();
    }

    boolean isContractMetadataSection(StageType stageType, int sectionNumber) {
        return (stageType == StageType.PRD && sectionNumber == 7)
                || (stageType == StageType.DESIGN && sectionNumber == 8);
    }
}
