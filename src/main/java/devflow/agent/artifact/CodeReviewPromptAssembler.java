package devflow.agent.artifact;

import devflow.agent.context.SourceMetadataKeys;
import devflow.agent.protocol.ArtifactBlockKind;
import org.springframework.lang.Nullable;

/**
 * CODE_REVIEW 阶段 prompt 组装器。
 *
 * <p>把 review prompt 规划从阶段门面中拆出，避免 `CodeReviewStageComposer`
 * 再次同时承担 intake 和 prompt plan 两类职责。
 */
final class CodeReviewPromptAssembler {

    CodeReviewPrompt build(@Nullable String note, String implementation, String contractView, String changes) {
        return new CodeReviewPrompt(buildSystemPrompt(), buildUserPrompt(note, implementation, contractView, changes));
    }

    private String buildSystemPrompt() {
        return """
                你是严格的资深代码审阅者。请结合实现报告和实际代码变更做 code review。
                输出 Markdown，不要输出额外解释。
                第一部分必须输出一个 REVIEW_RESULT 机器 block，格式如下：
                %s
                {
                  "decision": "APPROVED|REVISION_REQUIRED|REJECTED",
                  "fixMode": "NONE|PATCH|REWORK",
                  "summary": "一句话总结",
                  "changeRequest": "如需修改则写清楚，否则空字符串",
                  "evidence": "最关键的代码/测试证据，否则空字符串",
                  "actionItems": "coder 可直接执行的动作，否则空字符串",
                  "blockingFindings": true,
                  "findingCount": 1
                }
                %s

                第二部分紧跟一组显式 key-value 行，字段必须与 JSON block 一致：
                - decision: ...
                - fixMode: ...
                - summary: ...
                - changeRequest: ...
                - evidence: ...
                - actionItems: ...
                - blockingFindings: ...
                - findingCount: ...

                fixMode 规则：
                - APPROVED 时必须是 NONE
                - PATCH 表示结构基本可接受，只做增量修补
                - REWORK 表示结构存在明显问题，允许较大范围重构

                审阅约束：
                - Findings 只能写你能从“实际代码变更”或“实现报告”中直接证实的问题
                - 不要猜测“可能缺少某功能”，除非代码里确实没有对应实现证据
                - 只有 Execution Contract，以及 Source Metadata 中的 %s / %s 属于绑定约束；%s / %s / %s / %s 只能作为参考背景
                - 如果 decision=APPROVED，则 blockingFindings 必须为 false，findingCount 必须为 0
                - 如果 decision!=APPROVED，则 blockingFindings 必须为 true，findingCount 必须大于 0
                - 不要因为空目录、历史残留说明或已经删除的模块名称，就判定当前代码仍存在结构问题
                - 如果有问题，必须优先给出“具体问题”，不能只写空泛评价
                - 每条问题尽量落到具体文件、函数、变量、事件绑定、资源引用或测试证据
                - changeRequest 必须是 coder 可直接执行的修复动作，不要写成“请自行排查”
                """.formatted(
                ArtifactBlockKind.REVIEW_RESULT.beginMarker(),
                ArtifactBlockKind.REVIEW_RESULT.endMarker(),
                SourceMetadataKeys.HARD_USER_REQUIREMENTS,
                SourceMetadataKeys.HARD_UPSTREAM_FACTS,
                SourceMetadataKeys.SOFT_INFERENCES,
                SourceMetadataKeys.SOFT_DESIGN_DECISIONS,
                SourceMetadataKeys.SOFT_RECOMMENDATIONS,
                SourceMetadataKeys.OPEN_QUESTIONS
        );
    }

    private String buildUserPrompt(
            @Nullable String note,
            String implementation,
            String contractView,
            String changes
    ) {
        return """
                当前备注：
                %s

                实现报告：
                %s

                产品与设计约束（结构化 contract）：
                %s

                实际代码变更：
                %s

                输出要求：
                1. 必须先输出 REVIEW_RESULT JSON block
                2. 再输出 decision / fixMode / summary / changeRequest / evidence / actionItems / blockingFindings / findingCount 八行
                3. 然后给出 Findings
                4. Findings 只列真正的问题和风险，并尽量引用具体文件/代码证据
                5. 每条 Findings 用下面格式：
                   - [严重度] 文件或模块：具体问题。证据：xxx。建议：xxx。
                6. 如果 decision 不是 APPROVED，summary / changeRequest / evidence / actionItems 都必须概括最关键的 1-2 个具体问题
                7. 必须同时判断代码是否满足 PRD 与 DESIGN 中已经明确的目标、约束、运行形态和验收要求，而不只是看代码语法是否成立
                """.formatted(note, implementation, contractView, changes);
    }
}
