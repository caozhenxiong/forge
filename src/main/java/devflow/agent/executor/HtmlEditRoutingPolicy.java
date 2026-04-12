package devflow.agent.executor;

import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.EnumSet;

/**
 * 统一维护 HTML 宿主 patch、内联脚本/样式 patch 与 focused region 的路由条件。
 *
 * <p>这层只负责“该走哪条 HTML 编辑主链”的确定性判断，不负责真正执行编辑。
 * 这样上层路由层不需要自己维护多组 fallback 条件。
 */
final class HtmlEditRoutingPolicy {

    private static final EnumSet<GenerationFailureType> EMBEDDED_PATCH_FALLBACK_FAILURES = EnumSet.of(
            GenerationFailureType.TARGET_NOT_FOUND,
            GenerationFailureType.MODEL_OUTPUT_INVALID,
            GenerationFailureType.SNAPSHOT_STALE,
            GenerationFailureType.TARGET_SCOPE_VIOLATION,
            GenerationFailureType.SYNTAX_INVALID,
            GenerationFailureType.VALIDATION_FAILED
    );
    private static final EnumSet<GenerationFailureType> HOST_HTML_REGION_FAILURES = EnumSet.of(
            GenerationFailureType.OUTPUT_TRUNCATED,
            GenerationFailureType.MODEL_OUTPUT_INVALID,
            GenerationFailureType.SNAPSHOT_STALE,
            GenerationFailureType.VALIDATION_FAILED
    );

    private final EmbeddingAdapter<InlineScriptEditPlan> htmlInlineScriptEmbeddingAdapter;
    private final EmbeddingAdapter<InlineStyleEditPlan> htmlInlineStyleEmbeddingAdapter;
    private final HtmlFocusedRegionResolver htmlFocusedRegionResolver;

    HtmlEditRoutingPolicy(
            EmbeddingAdapter<InlineScriptEditPlan> htmlInlineScriptEmbeddingAdapter,
            EmbeddingAdapter<InlineStyleEditPlan> htmlInlineStyleEmbeddingAdapter,
            HtmlFocusedRegionResolver htmlFocusedRegionResolver
    ) {
        this.htmlInlineScriptEmbeddingAdapter = htmlInlineScriptEmbeddingAdapter;
        this.htmlInlineStyleEmbeddingAdapter = htmlInlineStyleEmbeddingAdapter;
        this.htmlFocusedRegionResolver = htmlFocusedRegionResolver;
    }

    EmbeddingAdapter<InlineScriptEditPlan> inlineScriptAdapter() {
        return htmlInlineScriptEmbeddingAdapter;
    }

    EmbeddingAdapter<InlineStyleEditPlan> inlineStyleAdapter() {
        return htmlInlineStyleEmbeddingAdapter;
    }

    boolean shouldFallbackToFocusedHtmlRegion(GenerationFailureReport report) {
        return report != null
                && report.usesPreciseEditingStrategy()
                && HOST_HTML_REGION_FAILURES.contains(report.failureType());
    }

    boolean shouldFallbackFromInlineScript(GenerationFailureReport report) {
        return report != null && EMBEDDED_PATCH_FALLBACK_FAILURES.contains(report.failureType());
    }

    boolean shouldFallbackFromInlineStyle(GenerationFailureReport report) {
        return report != null && EMBEDDED_PATCH_FALLBACK_FAILURES.contains(report.failureType());
    }

    boolean shouldUseInlineScriptWorkingSetEditing(
            Path relativePath,
            SubtaskExecutionState executionState,
            String existingContent,
            FileChange scopedChange
    ) {
        if (!supportsIncrementalHtmlEditing(relativePath, executionState)) {
            return false;
        }
        if (scopedChange != null && (scopedChange.hostHtmlPatchRequired()
                || scopedChange.effectiveEditScope() == FileEditScope.HOST_HTML_PATCH)) {
            return false;
        }
        if (scopedChange != null && scopedChange.effectiveEditScope() == FileEditScope.INLINE_STYLE_PATCH) {
            return false;
        }
        return htmlInlineScriptEmbeddingAdapter.supports(relativePath, existingContent);
    }

    boolean shouldUseInlineStyleWorkingSetEditing(
            Path relativePath,
            SubtaskExecutionState executionState,
            String existingContent,
            FileChange scopedChange
    ) {
        if (!supportsIncrementalHtmlEditing(relativePath, executionState)) {
            return false;
        }
        if (scopedChange != null && (scopedChange.hostHtmlPatchRequired()
                || scopedChange.effectiveEditScope() == FileEditScope.HOST_HTML_PATCH)) {
            return false;
        }
        if (scopedChange != null && scopedChange.effectiveEditScope() == FileEditScope.INLINE_SCRIPT_PATCH) {
            return false;
        }
        if (htmlInlineScriptEmbeddingAdapter.supports(relativePath, existingContent)) {
            return false;
        }
        return htmlInlineStyleEmbeddingAdapter.supports(relativePath, existingContent);
    }

    boolean shouldUseFocusedScriptRegionEditing(
            Path relativePath,
            SubtaskExecutionState executionState,
            String existingContent,
            FileChange scopedChange
    ) {
        if (!supportsIncrementalHtmlEditing(relativePath, executionState)) {
            return false;
        }
        if (scopedChange != null && (scopedChange.hostHtmlPatchRequired()
                || scopedChange.effectiveEditScope() == FileEditScope.HOST_HTML_PATCH)) {
            return false;
        }
        if (scopedChange != null && scopedChange.effectiveEditScope() == FileEditScope.INLINE_STYLE_PATCH) {
            return false;
        }
        return htmlFocusedRegionResolver.hasEditableRegion(existingContent, HtmlEditRegion.SCRIPT);
    }

    boolean shouldUseFocusedStyleRegionEditing(
            Path relativePath,
            SubtaskExecutionState executionState,
            String existingContent,
            FileChange scopedChange
    ) {
        if (!supportsIncrementalHtmlEditing(relativePath, executionState)) {
            return false;
        }
        if (scopedChange == null || scopedChange.effectiveEditScope() != FileEditScope.INLINE_STYLE_PATCH) {
            return false;
        }
        return htmlFocusedRegionResolver.hasEditableRegion(existingContent, HtmlEditRegion.STYLE);
    }

    private boolean supportsIncrementalHtmlEditing(Path relativePath, SubtaskExecutionState executionState) {
        if (!ProjectPathSupport.isHtml(relativePath)) {
            return false;
        }
        return executionState.deliveryMode() != DeliveryMode.SKELETON
                && executionState.deliveryMode() != DeliveryMode.REWORK;
    }
}
