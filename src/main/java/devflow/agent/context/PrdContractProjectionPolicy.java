package devflow.agent.context;

import devflow.agent.text.TextCanonicalizer;
import java.util.List;

/**
 * PRD -> PRODUCT_CONTRACT 投影策略。
 *
 * <p>这里只处理固定 PRD 章节条目的稳定筛选，不负责理解自由 prose 的业务语义。
 * 当前唯一职责是把显式“待确认 / 问题形式”的条目挡在 machine contract 之外，
 * 避免它们被错误升级成 required / optional / acceptance 承诺。
 *
 * <p>被筛掉的条目不会从原始 PRD 正文消失；它们只是不会进入 PRODUCT_CONTRACT，
 * 后续可继续走人工审阅，而不是污染 implementation coverage。
 */
final class PrdContractProjectionPolicy {

    /**
     * 这些 marker 代表条目已经显式标注为“未定项”，应保留给人工审阅而非机器契约。
     *
     * <p>统一收口在这里，避免主链继续散落魔法字符串。
     */
    private static final List<String> HUMAN_REVIEW_MARKERS = List.of(
            "待确认",
            "tobeconfirmed",
            "openquestion:"
    );

    /**
     * 只有明确的问题句式才做屏蔽，不扩展成对自然语言的泛化猜测。
     */
    private static final List<String> DIRECT_QUESTION_PREFIXES = List.of(
            "是否",
            "whether"
    );

    List<String> retainContractItems(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .filter(item -> !shouldRouteToHumanReview(item))
                .distinct()
                .toList();
    }

    boolean shouldRouteToHumanReview(String item) {
        if (item == null || item.isBlank()) {
            return false;
        }
        String collapsed = TextCanonicalizer.collapseWhitespace(item);
        String normalized = TextCanonicalizer.removeWhitespace(collapsed).toLowerCase();
        return hasHumanReviewMarker(normalized) || isDirectQuestion(collapsed, normalized);
    }

    private boolean hasHumanReviewMarker(String normalized) {
        for (String marker : HUMAN_REVIEW_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDirectQuestion(String collapsed, String normalized) {
        if (collapsed.indexOf('?') >= 0 || collapsed.indexOf('？') >= 0) {
            return true;
        }
        for (String prefix : DIRECT_QUESTION_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
