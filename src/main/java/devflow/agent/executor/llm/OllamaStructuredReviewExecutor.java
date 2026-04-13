package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationTelemetry;

import com.fasterxml.jackson.annotation.JsonProperty;
import devflow.agent.executor.ChangeAction;
import devflow.agent.executor.FileChange;
import devflow.agent.executor.editing.FileEditScope;
import devflow.agent.executor.runtime.RuntimeOwnershipMode;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.review.ReviewSemantics;
import devflow.agent.review.StructuredReviewResult;
import devflow.agent.text.TextCanonicalizer;
import devflow.agent.util.EnumParsers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama 结构化审阅执行器。
 *
 * <p>负责：
 * 1. 构造 review JSON 协议 prompt；
 * 2. 解析 structured payload；
 * 3. 将 review 语义转成系统内部结构化结果。
 */
final class OllamaStructuredReviewExecutor {

    private final StructuredPayloadReader structuredPayloadReader;
    private final OllamaGenerationExecutor generationExecutor;

    OllamaStructuredReviewExecutor(
            StructuredPayloadReader structuredPayloadReader,
            OllamaGenerationExecutor generationExecutor
    ) {
        this.structuredPayloadReader = structuredPayloadReader;
        this.generationExecutor = generationExecutor;
    }

    StructuredReviewResult reviewStructured(
            String systemPrompt,
            String candidateContent,
            Map<String, Object> options,
            ModelRole role
    ) {
        String prompt = """
                请对下面的内容做审阅，必须只返回一个 JSON 对象，格式如下：
                {
                  "decision": "APPROVED|REVISION_REQUIRED|REJECTED",
                  "fixMode": "NONE|PATCH|REWORK",
                  "implementationPatchTarget": "NONE|PATCH_EXISTING_IMPLEMENTATION|PATCH_RUNTIME_WIRING",
                  "overrideChanges": [
                    {
                      "path": "index.html",
                      "action": "WRITE",
                      "reason": "简短原因",
                      "editScope": "AUTO|HOST_HTML_PATCH|INLINE_SCRIPT_PATCH|INLINE_STYLE_PATCH",
                      "runtimeOwnership": "INLINE_HOST|EXTERNAL_COMPANION|null",
                      "hostHtmlPatchRequired": true
                    }
                  ],
                  "summary": "不超过60字",
                  "changeRequest": "不超过120字，无则返回空字符串",
                  "evidence": "不超过160字，写清关键证据，无则返回空字符串",
                  "actionItems": "不超过200字，给 coder 可直接执行的动作，无则返回空字符串",
                  "semantics": {
                    "targetsLowAuthorityContent": true,
                    "targetsTrackedOpenQuestion": false,
                    "clarificationRequest": false,
                    "backedByHardAuthority": false,
                    "requestsQuantitativeHardening": false,
                    "requestsImplementationHardening": false,
                    "downstreamDetailOnly": false,
                    "coreStageGap": true,
                    "performanceClaim": false,
                    "measurementEvidencePresent": false,
                    "unsupportedQuantitativeConstraintPresent": false,
                    "unsupportedImplementationConstraintPresent": false
                  }
                }

                约束：
                1. 只能返回 JSON，不要 markdown，不要 ```json
                2. 内容必须简短，避免长段落
                3. 如果通过，fixMode 必须为 NONE，changeRequest 必须为空字符串
                4. 如果不通过，fixMode 必须明确选择 PATCH 或 REWORK
                5. PATCH 表示只做局部修补，REWORK 表示允许较大范围重构
                5.1 非 implementation review 或 fixMode!=PATCH 时，implementationPatchTarget 必须返回 NONE
                5.2 implementation review 且 fixMode=PATCH 时，implementationPatchTarget 必须返回具体值：
                    - 一般基于现有实现补局部能力/修编译或测试问题，用 PATCH_EXISTING_IMPLEMENTATION
                    - 已有运行时结构下的接线/引用/初始化问题，用 PATCH_RUNTIME_WIRING
                5.3 implementationPatchTarget=PATCH_EXISTING_IMPLEMENTATION 时，overrideChanges 必须非空，且只列本轮需要 patch 的文件
                5.4 implementationPatchTarget=PATCH_RUNTIME_WIRING 时，overrideChanges 必须为空数组
                5.5 decision=APPROVED 或 fixMode=REWORK 时，overrideChanges 必须为空数组
                6. evidence 必须写清支持结论的代码/测试/自检证据
                7. actionItems 必须是可执行动作，优先写文件、函数、模块、验证步骤
                8. semantics 必须是你对本次审阅语义的结构化判断，不要省略任何字段
                9. targetsLowAuthorityContent 表示 finding 主要针对推断/建议/设计选择/低权重内容
                10. targetsTrackedOpenQuestion 表示 finding 主要针对文档里已经显式标为待确认/开放问题的内容
                11. clarificationRequest 表示本次 finding 的核心诉求是“请明确/补充/说明/确认”
                12. backedByHardAuthority 表示该 finding 的要求确实由用户要求、上游事实或文档中的 hard/source metadata 支撑
                13. requestsQuantitativeHardening 表示 finding 要求新增量化指标、时延/FPS/数值门槛等硬化约束
                14. requestsImplementationHardening 表示 finding 要求新增实现细节、模块/文件/技术选型等实现约束
                15. downstreamDetailOnly 表示 finding 只是在追打更下游阶段的细节，而不是当前阶段核心缺口
                16. coreStageGap 表示 finding 确实指出了当前阶段本应具备的核心缺口
                17. performanceClaim 表示 finding 在做性能结论或性能否定判断
                18. measurementEvidencePresent 表示 evidence 或输入中已经提供了明确测量数据，可支撑性能结论
                19. unsupportedQuantitativeConstraintPresent 表示候选文档本身已经写入了无来源支撑的量化性能指标或数值门槛
                20. unsupportedImplementationConstraintPresent 表示候选文档本身已经写入了无来源支撑的实现组织、交付形态、文件组织或技术硬约束

                待审阅内容：
                %s
                """.formatted(candidateContent);
        Map<String, Object> effectiveOptions = options == null ? Map.of() : new LinkedHashMap<>(options);
        String content = generationExecutor.generate(
                LlmGenerateRequest.workingPrompt(systemPrompt, prompt, effectiveOptions, role)
        );
        try {
            ReviewPayload payload = structuredPayloadReader.readJsonObject(content, ReviewPayload.class);
            ReviewDecision decision = ReviewDecision.valueOf(payload.decision());
            return new StructuredReviewResult(
                    new ReviewResult(
                            decision,
                            payload.fixMode() == null ? defaultFixMode(decision) : FixMode.valueOf(payload.fixMode()),
                            payload.summary(),
                            payload.changeRequest(),
                            payload.evidence(),
                            payload.actionItems(),
                            parsePatchTarget(payload.implementationPatchTarget()),
                            parseOverrideChanges(payload.overrideChanges())
                    ),
                    payload.semantics() == null ? ReviewSemantics.empty() : payload.semantics().toReviewSemantics()
            );
        } catch (Exception exception) {
            throw new StructuredPayloadException(
                    StructuredPayloadFailureReason.JSON_PAYLOAD_INVALID,
                    "Failed to parse structured review payload from Ollama: " + sanitizeFallback(content)
            );
        }
    }

    GenerationTelemetry consumeLastTelemetry() {
        return generationExecutor.consumeLastTelemetry();
    }

    private String sanitizeFallback(String content) {
        String singleLine = content.replace("```json", "")
                .replace("```", "")
                .trim();
        singleLine = TextCanonicalizer.collapseWhitespace(singleLine);
        return singleLine.length() > 120 ? singleLine.substring(0, 120) : singleLine;
    }

    private FixMode defaultFixMode(ReviewDecision decision) {
        return decision == ReviewDecision.APPROVED ? FixMode.NONE : FixMode.PATCH;
    }

    private ImplementationPatchTarget parsePatchTarget(String value) {
        if (value == null || value.isBlank()) {
            return ImplementationPatchTarget.NONE;
        }
        try {
            return ImplementationPatchTarget.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return ImplementationPatchTarget.NONE;
        }
    }

    private record ReviewPayload(
            @JsonProperty("decision") String decision,
            @JsonProperty("fixMode") String fixMode,
            @JsonProperty("implementationPatchTarget") String implementationPatchTarget,
            @JsonProperty("overrideChanges") List<FileChangePayload> overrideChanges,
            @JsonProperty("summary") String summary,
            @JsonProperty("changeRequest") String changeRequest,
            @JsonProperty("evidence") String evidence,
            @JsonProperty("actionItems") String actionItems,
            @JsonProperty("semantics") ReviewSemanticsPayload semantics
    ) {
    }

    private record ReviewSemanticsPayload(
            @JsonProperty("targetsLowAuthorityContent") Boolean targetsLowAuthorityContent,
            @JsonProperty("targetsTrackedOpenQuestion") Boolean targetsTrackedOpenQuestion,
            @JsonProperty("clarificationRequest") Boolean clarificationRequest,
            @JsonProperty("backedByHardAuthority") Boolean backedByHardAuthority,
            @JsonProperty("requestsQuantitativeHardening") Boolean requestsQuantitativeHardening,
            @JsonProperty("requestsImplementationHardening") Boolean requestsImplementationHardening,
            @JsonProperty("downstreamDetailOnly") Boolean downstreamDetailOnly,
            @JsonProperty("coreStageGap") Boolean coreStageGap,
            @JsonProperty("performanceClaim") Boolean performanceClaim,
            @JsonProperty("measurementEvidencePresent") Boolean measurementEvidencePresent,
            @JsonProperty("unsupportedQuantitativeConstraintPresent") Boolean unsupportedQuantitativeConstraintPresent,
            @JsonProperty("unsupportedImplementationConstraintPresent") Boolean unsupportedImplementationConstraintPresent
    ) {
        private ReviewSemantics toReviewSemantics() {
            return new ReviewSemantics(
                    true,
                    Boolean.TRUE.equals(targetsLowAuthorityContent),
                    Boolean.TRUE.equals(targetsTrackedOpenQuestion),
                    Boolean.TRUE.equals(clarificationRequest),
                    Boolean.TRUE.equals(backedByHardAuthority),
                    Boolean.TRUE.equals(requestsQuantitativeHardening),
                    Boolean.TRUE.equals(requestsImplementationHardening),
                    Boolean.TRUE.equals(downstreamDetailOnly),
                    Boolean.TRUE.equals(coreStageGap),
                    Boolean.TRUE.equals(performanceClaim),
                    Boolean.TRUE.equals(measurementEvidencePresent),
                    Boolean.TRUE.equals(unsupportedQuantitativeConstraintPresent),
                    Boolean.TRUE.equals(unsupportedImplementationConstraintPresent)
            );
        }
    }

    private List<FileChange> parseOverrideChanges(List<FileChangePayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }
        return payloads.stream()
                .filter(payload -> payload != null && payload.path() != null && !payload.path().isBlank())
                .map(payload -> new FileChange(
                        payload.path(),
                        EnumParsers.parseIgnoreCase(ChangeAction.class, payload.action(), ChangeAction.WRITE),
                        payload.reason() == null ? "" : payload.reason(),
                        EnumParsers.parseIgnoreCase(FileEditScope.class, payload.editScope(), FileEditScope.AUTO),
                        EnumParsers.parseIgnoreCase(RuntimeOwnershipMode.class, payload.runtimeOwnership(), null),
                        Boolean.TRUE.equals(payload.hostHtmlPatchRequired())
                ))
                .toList();
    }

    private record FileChangePayload(
            @JsonProperty("path") String path,
            @JsonProperty("action") String action,
            @JsonProperty("reason") String reason,
            @JsonProperty("editScope") String editScope,
            @JsonProperty("runtimeOwnership") String runtimeOwnership,
            @JsonProperty("hostHtmlPatchRequired") Boolean hostHtmlPatchRequired
    ) {
    }
}
