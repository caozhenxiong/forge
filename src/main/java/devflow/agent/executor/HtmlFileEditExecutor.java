package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureException;
import devflow.agent.executor.generation.GenerationFailureType;

import devflow.agent.i18n.PlaceholderValues;

/**
 * HTML 文件 patch 执行门面。
 *
 * <p>它统一接管：
 * 1. 宿主 HTML 结构草稿；
 * 2. 宿主内联脚本 patch；
 * 3. 宿主内联样式 patch；
 * 4. precise-html / focused-region；
 * 5. HTML 文件的 whole-file 例外路径。
 *
 * <p>这样协调器只需要判断“当前是不是 HTML 文件”，不再保留整段宿主路由树。
 */
final class HtmlFileEditExecutor {

    private final FileEditStrategyResolver fileEditStrategyResolver;
    private final HtmlEditRoutingPolicy htmlEditRoutingPolicy;
    private final HtmlFocusedRegionResolver htmlFocusedRegionResolver;
    private final HostHtmlPatchExecutor hostHtmlPatchExecutor;
    private final EmbeddedPatchExecutor embeddedPatchExecutor;
    private final FullRewriteExecutor wholeFilePatchExecutor;
    private final FileProtocolRequestFactory patchRequestFactory;
    private final FileGenerationFailureFactory fileGenerationFailureFactory;
    private final int maxFileGenerationAttempts;

    HtmlFileEditExecutor(
            FileEditStrategyResolver fileEditStrategyResolver,
            HtmlEditRoutingPolicy htmlEditRoutingPolicy,
            HtmlFocusedRegionResolver htmlFocusedRegionResolver,
            HostHtmlPatchExecutor hostHtmlPatchExecutor,
            EmbeddedPatchExecutor embeddedPatchExecutor,
            FullRewriteExecutor wholeFilePatchExecutor,
            FileProtocolRequestFactory patchRequestFactory,
            FileGenerationFailureFactory fileGenerationFailureFactory,
            int maxFileGenerationAttempts
    ) {
        this.fileEditStrategyResolver = fileEditStrategyResolver;
        this.htmlEditRoutingPolicy = htmlEditRoutingPolicy;
        this.htmlFocusedRegionResolver = htmlFocusedRegionResolver;
        this.hostHtmlPatchExecutor = hostHtmlPatchExecutor;
        this.embeddedPatchExecutor = embeddedPatchExecutor;
        this.wholeFilePatchExecutor = wholeFilePatchExecutor;
        this.patchRequestFactory = patchRequestFactory;
        this.fileGenerationFailureFactory = fileGenerationFailureFactory;
        this.maxFileGenerationAttempts = maxFileGenerationAttempts;
    }

    GeneratedFileOutput generate(FileEditRequest request) {
        FileEditAttemptState editAttemptState = request.editAttemptState();
        if (editAttemptState != null && editAttemptState.resumable()) {
            String strategyName = editAttemptState.strategyName();
            if (FileEditStrategyNames.INLINE_STYLE_WORKSET.equals(strategyName)) {
                return GeneratedFileOutput.primaryOnly(embeddedPatchExecutor.generate(
                        htmlEditRoutingPolicy.inlineStyleAdapter(),
                        patchRequestFactory.embedded(request),
                        EmbeddedPatchKind.STYLE,
                        "HTML 内联 style 未暴露稳定锚点或缺少可精确编辑的样式规则。",
                        "请改用 HTML 区块级 style 改写，不要直接退回整文件重写。",
                        "请保持主样式工作集改写后的 HTML 整体可解析。",
                        false
                ).primaryContent());
            }
            if (FileEditStrategyNames.PRECISE_HTML.equals(strategyName)) {
                return GeneratedFileOutput.primaryOnly(hostHtmlPatchExecutor.generatePreciseHtml(
                        patchRequestFactory.hostHtml(request)
                ));
            }
            if (strategyName.startsWith(FileEditStrategyNames.FOCUSED_HTML_REGION)) {
                return GeneratedFileOutput.primaryOnly(hostHtmlPatchExecutor.generateFocusedRegion(
                        patchRequestFactory.hostHtml(request),
                        parseFocusedRegion(strategyName)
                ));
            }
        }
        if (fileEditStrategyResolver.shouldUseStructuredHtmlDocumentGeneration(
                request.projectPath(),
                request.relativePath(),
                request.existingContent().isBlank()
                        ? PlaceholderValues.machineNewFile()
                        : request.existingContent()
        )) {
            return GeneratedFileOutput.primaryOnly(hostHtmlPatchExecutor.generateStructuredHtmlDocument(
                    patchRequestFactory.hostHtml(request)
            ));
        }
        if (shouldExternalizeInlineScriptBeforeEditing(request)) {
            return embeddedPatchExecutor.externalizeExistingScript(
                    htmlEditRoutingPolicy.inlineScriptAdapter(),
                    patchRequestFactory.embedded(request),
                    "当前 HTML 入口已显式选择 EXTERNAL_COMPANION runtimeOwnership，应先把现有内联主脚本外提到 companion runtime，再继续代码级增量编辑。"
            );
        }
        if (htmlEditRoutingPolicy.shouldUseInlineScriptWorkingSetEditing(
                request.relativePath(),
                request.executionState(),
                request.existingContent(),
                request.scopedChange()
        )) {
            try {
                return embeddedPatchExecutor.generate(
                        htmlEditRoutingPolicy.inlineScriptAdapter(),
                        patchRequestFactory.embedded(request),
                        EmbeddedPatchKind.SCRIPT,
                        "HTML 内联 script 未暴露稳定锚点或缺少可精确编辑的脚本符号。",
                        "请改用 HTML 区块级改写，不要退回整文件重写。",
                        "请保持主脚本工作集改写后的 HTML 整体可解析。",
                        true
                );
            } catch (GenerationFailureException exception) {
                if (!htmlEditRoutingPolicy.shouldFallbackFromInlineScript(exception.report())) {
                    throw exception;
                }
                if (htmlEditRoutingPolicy.shouldUseFocusedScriptRegionEditing(
                        request.relativePath(),
                        request.executionState(),
                        request.existingContent(),
                        request.scopedChange()
                )) {
                    return GeneratedFileOutput.primaryOnly(hostHtmlPatchExecutor.generateFocusedRegion(
                            patchRequestFactory.hostHtml(request),
                            HtmlEditRegion.SCRIPT
                    ));
                }
            }
        }
        // 对 inline script 来说，是否继续走 workset 必须先基于当前宿主内容重判。
        // 不能因为旧 patchProgress 里残留着 inline-script-workset，就继续把已经失配的骨架强行续跑。
        if (htmlEditRoutingPolicy.shouldUseFocusedScriptRegionEditing(
                request.relativePath(),
                request.executionState(),
                request.existingContent(),
                request.scopedChange()
        )) {
            return GeneratedFileOutput.primaryOnly(hostHtmlPatchExecutor.generateFocusedRegion(
                    patchRequestFactory.hostHtml(request),
                    HtmlEditRegion.SCRIPT
            ));
        }
        if (htmlEditRoutingPolicy.shouldUseInlineStyleWorkingSetEditing(
                request.relativePath(),
                request.executionState(),
                request.existingContent(),
                request.scopedChange()
        )) {
            try {
                return GeneratedFileOutput.primaryOnly(embeddedPatchExecutor.generate(
                        htmlEditRoutingPolicy.inlineStyleAdapter(),
                        patchRequestFactory.embedded(request),
                        EmbeddedPatchKind.STYLE,
                        "HTML 内联 style 未暴露稳定锚点或缺少可精确编辑的样式规则。",
                        "请改用 HTML 区块级 style 改写，不要直接退回整文件重写。",
                        "请保持主样式工作集改写后的 HTML 整体可解析。",
                        false
                ).primaryContent());
            } catch (GenerationFailureException exception) {
                if (!htmlEditRoutingPolicy.shouldFallbackFromInlineStyle(exception.report())) {
                    throw exception;
                }
                if (htmlFocusedRegionResolver.hasEditableRegion(request.existingContent(), HtmlEditRegion.STYLE)) {
                    return GeneratedFileOutput.primaryOnly(hostHtmlPatchExecutor.generateFocusedRegion(
                            patchRequestFactory.hostHtml(request),
                            HtmlEditRegion.STYLE
                    ));
                }
            }
        }
        if (htmlEditRoutingPolicy.shouldUseFocusedStyleRegionEditing(
                request.relativePath(),
                request.executionState(),
                request.existingContent(),
                request.scopedChange()
        )) {
            return GeneratedFileOutput.primaryOnly(hostHtmlPatchExecutor.generateFocusedRegion(
                    patchRequestFactory.hostHtml(request),
                    HtmlEditRegion.STYLE
            ));
        }
        if (fileEditStrategyResolver.shouldUsePreciseHtmlEditing(
                request.projectPath(),
                request.relativePath(),
                request.executionState().deliveryMode(),
                request.executionState().preferPreciseEditing(),
                request.existingContent()
        )) {
            try {
                return GeneratedFileOutput.primaryOnly(hostHtmlPatchExecutor.generatePreciseHtml(
                        patchRequestFactory.hostHtml(request)
                ));
            } catch (GenerationFailureException exception) {
                if (htmlEditRoutingPolicy.shouldFallbackToFocusedHtmlRegion(exception.report())) {
                    return GeneratedFileOutput.primaryOnly(hostHtmlPatchExecutor.generateFocusedRegion(
                            patchRequestFactory.hostHtml(request),
                            null
                    ));
                }
                throw exception;
            }
        }
        if (!fileEditStrategyResolver.canUseWholeFileRewriteForHtml(
                request.projectPath(),
                request.relativePath(),
                request.executionState().deliveryMode()
        )) {
            throw fileGenerationFailureFactory.create(
                    request.relativePath(),
                    request.executionState().deliveryMode(),
                    FileEditStrategyNames.FULL_FILE_DISALLOWED,
                    maxFileGenerationAttempts,
                    GenerationFailureType.VALIDATION_FAILED,
                    "现有 HTML 入口文件不允许在增量模式下退回整页重写。",
                    "请继续使用内联脚本工作集、聚焦区块或 precise-html 局部改写。"
            );
        }
        return GeneratedFileOutput.primaryOnly(wholeFilePatchExecutor.generate(
                patchRequestFactory.wholeFile(request)
        ));
    }

    private boolean shouldExternalizeInlineScriptBeforeEditing(FileEditRequest request) {
        if (request == null || request.subtask() == null || request.executionState() == null) {
            return false;
        }
        if (!htmlEditRoutingPolicy.shouldUseInlineScriptWorkingSetEditing(
                request.relativePath(),
                request.executionState(),
                request.existingContent(),
                request.scopedChange()
        )) {
            return false;
        }
        HtmlRuntimeOwnershipContract runtimeContract = request.runtimeContract();
        if (runtimeContract == null || !runtimeContract.externalCompanion() || runtimeContract.runtimePaths().isEmpty()) {
            return false;
        }
        return request.subtask().changes() != null
                && request.subtask().changes().stream()
                .filter(change -> change != null && change.path() != null && !change.path().isBlank())
                .map(change -> java.nio.file.Path.of(change.path()).normalize())
                .anyMatch(runtimeContract.runtimePaths()::contains);
    }

    private HtmlEditRegion parseFocusedRegion(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            return null;
        }
        String suffix = strategyName.substring(FileEditStrategyNames.FOCUSED_HTML_REGION.length());
        if (suffix.startsWith("-")) {
            suffix = suffix.substring(1);
        }
        if (suffix.isBlank()) {
            return null;
        }
        return HtmlEditRegion.valueOf(suffix.toUpperCase(java.util.Locale.ROOT));
    }
}
