package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.ByteRange;
import devflow.agent.parsing.HtmlEditableStructure;
import devflow.agent.parsing.TreeSitterSupport;
import java.util.List;

/**
 * 负责从现有 HTML 结构中选择“更小但仍然有意义”的聚焦改写区块。
 *
 * <p>这里不再按“哪个区块最大”来退化，因为那会在截断后继续把模型推回大输出。
 * 退化优先级固定为：
 * 1. script：最贴近行为逻辑，通常也是增量修改最稳定的锚点；
 * 2. markup：页面结构改动；
 * 3. style：样式微调。
 *
 * <p>这个顺序只依赖 HTML 可编辑锚点，不依赖具体业务场景。
 */
public class HtmlFocusedRegionResolver {

    private final TreeSitterSupport treeSitterSupport;

    public HtmlFocusedRegionResolver(TreeSitterSupport treeSitterSupport) {
        this.treeSitterSupport = treeSitterSupport;
    }

    public HtmlEditRegion resolvePrimaryRegion(String htmlSource) {
        HtmlEditableStructure structure = treeSitterSupport.inspectEditableHtml(htmlSource);
        List<RegionCandidate> candidates = List.of(
                candidate(HtmlEditRegion.SCRIPT, structure.appScriptInnerRange()),
                candidate(HtmlEditRegion.MARKUP, structure.appRootInnerRange()),
                candidate(HtmlEditRegion.STYLE, structure.appStyleInnerRange())
        );
        return candidates.stream()
                .filter(candidate -> candidate.length() > 0)
                .map(RegionCandidate::region)
                .findFirst()
                .orElse(HtmlEditRegion.MARKUP);
    }

    /**
     * 当上层已经知道更稳定的首选区块时，优先用这个区块；只有缺失锚点时才退回默认策略。
     */
    public HtmlEditRegion resolvePreferredRegion(String htmlSource, HtmlEditRegion preferredRegion) {
        if (preferredRegion == null) {
            return resolvePrimaryRegion(htmlSource);
        }
        return hasEditableRegion(htmlSource, preferredRegion) ? preferredRegion : resolvePrimaryRegion(htmlSource);
    }

    public boolean hasEditableRegion(String htmlSource, HtmlEditRegion region) {
        if (region == null) {
            return false;
        }
        HtmlEditableStructure structure = treeSitterSupport.inspectEditableHtml(htmlSource);
        if (region == HtmlEditRegion.SCRIPT) {
            return validRange(structure.appScriptInnerRange());
        }
        if (region == HtmlEditRegion.MARKUP) {
            return validRange(structure.appRootInnerRange());
        }
        return validRange(structure.appStyleInnerRange());
    }

    private boolean validRange(ByteRange range) {
        return range != null && range.isValid();
    }

    private RegionCandidate candidate(HtmlEditRegion region, ByteRange range) {
        if (range == null || !range.isValid()) {
            return new RegionCandidate(region, 0);
        }
        return new RegionCandidate(region, Math.max(0, range.endByte() - range.startByte()));
    }

    private record RegionCandidate(HtmlEditRegion region, int length) {
    }
}
