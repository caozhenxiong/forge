package devflow.agent.context;

import java.util.List;

/**
 * PRD -> PRODUCT_CONTRACT 投影策略。
 *
 * <p>这里只处理固定 PRD 章节条目的稳定筛选，不负责理解自由 prose 的业务语义。
 * 当前职责是把显式低权重条目挡在 machine contract 之外，避免它们被错误升级成
 * required / optional / acceptance 承诺。
 *
 * <p>被筛掉的条目不会从原始 PRD 正文消失；它们只是不会进入 PRODUCT_CONTRACT，
 * 后续可继续走人工审阅，而不是污染 implementation coverage。
 */
final class PrdContractProjectionPolicy {

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
        return SourceMetadataRoutingPolicy.shouldStayOutOfContract(item);
    }
}
