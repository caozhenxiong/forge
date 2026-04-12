package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;

/**
 * requirement ref 的唯一覆盖义务等级。
 *
 * <p>这里不再用单个 boolean 混装“本轮必须实现 / 最终验收关注 / 可选增强 / 待确认”这几种语义，
 * 避免 planner、gate、review 继续围绕 prose 和布尔位打架。
 */
public enum CoverageObligation {
    PLANNING_REQUIRED(3),
    FINAL_ACCEPTANCE(2),
    OPTIONAL(1),
    OPEN_QUESTION(0);

    private final int priority;

    CoverageObligation(int priority) {
        this.priority = priority;
    }

    public boolean isPlanningRequired() {
        return this == PLANNING_REQUIRED;
    }

    public String markdownLabel(DocumentLanguage language) {
        return switch (this) {
            case PLANNING_REQUIRED -> language.choose("planning-required", "planning-required");
            case FINAL_ACCEPTANCE -> language.choose("final-acceptance", "final-acceptance");
            case OPTIONAL -> language.choose("optional", "optional");
            case OPEN_QUESTION -> language.choose("open-question", "open-question");
        };
    }

    public static CoverageObligation merge(CoverageObligation left, CoverageObligation right) {
        CoverageObligation normalizedLeft = left == null ? FINAL_ACCEPTANCE : left;
        CoverageObligation normalizedRight = right == null ? FINAL_ACCEPTANCE : right;
        return normalizedLeft.priority >= normalizedRight.priority ? normalizedLeft : normalizedRight;
    }
}
