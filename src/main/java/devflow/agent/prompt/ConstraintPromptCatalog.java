package devflow.agent.prompt;

import devflow.agent.context.ContractMetadataKeys;
import devflow.agent.context.SourceMetadataKeys;
import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.StageType;

/**
 * 约束与 metadata prompt 目录。
 *
 * <p>只负责 source/contract metadata、约束语义和直启交付说明，
 * 避免阶段 prompt 与约束协议长期混在一个 catalog 里。
 */
final class ConstraintPromptCatalog {

    String directLaunchClarification(DocumentLanguage language) {
        return language.choose(
                "如果约束写的是“可直接打开运行”“无需编译或打包”或类似表述，只能把它解释为需要可启动的入口与可运行交付物。默认使用 runtime.entryPackagingMode=entry-with-local-dependencies，不要在用户未明确提出时额外收紧为 self-contained-entry，也不要擅自收紧资源组织、文件数量、实现组织或交付形态；未经来源支撑的实现细节只能作为建议、设计选择或待确认问题。",
                "If constraints say the project should open directly or run without build/packaging, interpret that only as requiring a launchable entry and a runnable deliverable. Default to runtime.entryPackagingMode=entry-with-local-dependencies; do not tighten it to self-contained-entry unless the user explicitly requires that shape, and do not further tighten resource organization, file count, implementation organization, or delivery shape without source support. Unsupported implementation details must remain recommendations, design choices, or open questions."
        );
    }

    String sourceMetadataRequirement(DocumentLanguage language, int sectionNumber) {
        String heading = ArtifactLabels.numberedHeading(sectionNumber, ArtifactLabels.sourceMetadata(language));
        String keyLines = SourceMetadataKeys.allKeys().stream()
                .map(key -> "   " + SourceMetadataKeys.markdownLinePrefix(key))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return language.choose(
                """
                必须填写“%s”章节，并严格使用以下英文键：
                %s
                每个键使用逗号分隔短语；没有内容时写 (none)。
                """.formatted(heading, keyLines).trim(),
                """
                You must fill the “%s” section using these exact English keys:
                %s
                Use comma-separated phrases for each key; write (none) when empty.
                """.formatted(heading, keyLines).trim()
        );
    }

    String sourceMetadataSemantics(DocumentLanguage language) {
        String hardUserRequirements = SourceMetadataKeys.HARD_USER_REQUIREMENTS;
        String hardUpstreamFacts = SourceMetadataKeys.HARD_UPSTREAM_FACTS;
        String softInferences = SourceMetadataKeys.SOFT_INFERENCES;
        String softDesignDecisions = SourceMetadataKeys.SOFT_DESIGN_DECISIONS;
        String softRecommendations = SourceMetadataKeys.SOFT_RECOMMENDATIONS;
        String openQuestions = SourceMetadataKeys.OPEN_QUESTIONS;
        return language.choose(
                "只有 %s 与 %s 可以表达硬约束；soft.* 和 %s 只能作为参考信息，不能伪装成必须满足的硬要求。正文中带绑定语义的章节（例如关键约束、非功能要求、验收标准、执行契约）只能复述或细化 hard.* 已支撑的内容。凡是在正文里显式写出的“推断：/设计选择：/建议：/待确认问题：”内容，都必须同步写入 Source Metadata 对应的 %s / %s / %s / %s，不能只写正文不写 metadata。".formatted(
                        hardUserRequirements,
                        hardUpstreamFacts,
                        openQuestions,
                        softInferences,
                        softDesignDecisions,
                        softRecommendations,
                        openQuestions
                ),
                "Only %s and %s may represent binding constraints; soft.* and %s are reference-only and must not be disguised as mandatory requirements. Body sections with binding semantics (for example key constraints, non-functional requirements, acceptance criteria, or execution contracts) may only restate or refine content already supported by hard.* metadata. Any body content explicitly labeled as “Inference:”, “Design Choice:”, “Recommendation:”, or “Open Question:” must also be mirrored into the corresponding %s / %s / %s / %s entry in Source Metadata instead of living only in the body.".formatted(
                        hardUserRequirements,
                        hardUpstreamFacts,
                        openQuestions,
                        softInferences,
                        softDesignDecisions,
                        softRecommendations,
                        openQuestions
                )
        );
    }

    String sourceAndConstraintGuidance(DocumentLanguage language, StageType stageType) {
        String hardAuthorityPairZh = SourceMetadataKeys.HARD_USER_REQUIREMENTS + " 或 " + SourceMetadataKeys.HARD_UPSTREAM_FACTS;
        String hardAuthorityPairEn = SourceMetadataKeys.HARD_USER_REQUIREMENTS + " or " + SourceMetadataKeys.HARD_UPSTREAM_FACTS;
        String stageSpecific = switch (stageType) {
            case ANALYSIS -> language.choose(
                    "本阶段只负责识别用户目标、边界、执行需求和待确认问题；不要把推断、建议或偏好的实现方式升级成硬约束。正文里所有带“必须/需要/应当/交付约束/成功标准”语义的内容，都必须能在 %s 中找到来源支撑；否则应改写为推断、建议或待确认问题。".formatted(hardAuthorityPairZh),
                    "This stage only identifies user goals, boundaries, execution needs, and open questions; do not promote inferences, recommendations, or preferred implementation styles into hard constraints. Any body content with binding semantics such as must/required/constraints/success criteria must be supported by %s; otherwise rewrite it as an inference, recommendation, or open question.".formatted(hardAuthorityPairEn)
            );
            case PRD -> language.choose(
                    "本阶段关注用户可感知的能力、场景和验收结果；如果某种实现方式不是用户明确要求，只能把它写成建议、假设或待确认点，不能写成必须满足的硬要求。非功能要求、兼容性、部署方式、验收标准中的绑定条目，也必须有 hard.* 来源支撑；没有来源时只能降级为建议或待确认。",
                    "This stage focuses on user-visible capabilities, scenarios, and acceptance outcomes; if an implementation style was not explicitly required by the user, present it only as a recommendation, assumption, or open question rather than a mandatory requirement. Binding items in non-functional requirements, compatibility, delivery, or acceptance sections must also be supported by hard.* metadata; without such support, downgrade them to recommendations or open questions."
            );
            case DESIGN -> language.choose(
                    "上游文档中的技术实现倾向、文件组织暗示和资源组织偏好仅作为低权重参考；优先考虑模块划分合理性、契约一致性、可维护性、可扩展性与方案优雅性。若做出设计选择，必须把它表达为设计决策，而不是伪装成用户要求或既定事实；当多种实现组织方式都能满足 Execution Contract 与 hard.* 时，优先保留可替换性与模块边界，不要无来源地把文件组织、资源组织、部署形态或技术细节收紧为唯一方案。设计方案中的绑定部分只能来自 Execution Contract 与 hard.*，其余架构、文件组织、技术选型都必须明确标成设计决策、建议或开放问题。",
                    "Technical implementation tendencies, file-organization hints, and resource-organization preferences from upstream documents are only low-priority references; prioritize sound module boundaries, contract consistency, maintainability, extensibility, and design elegance. If you make a design choice, present it as a design decision rather than disguising it as a user requirement or an established fact; when multiple implementation organizations satisfy the Execution Contract and hard.* metadata, prefer preserving replaceability and module boundaries instead of narrowing file layout, resource organization, delivery shape, or technical details into a single source-unsupprted solution. Binding portions of the design may only come from the Execution Contract and hard.* metadata; all other architecture, file-organization, and technology choices must be labeled as design decisions, recommendations, or open questions."
            );
            default -> language.choose(
                    "区分用户要求、上游事实、推断、设计选择、建议和待确认问题。低确定性的内容不能升级成高确定性的硬约束。",
                    "Distinguish user requirements, upstream facts, inferences, design decisions, recommendations, and open questions. Lower-certainty content must not be promoted into higher-certainty hard constraints."
            );
        };
        return language.choose(
                stageType == StageType.PRD
                        ? stageSpecific + " 请显式区分“用户明确要求 / 上游事实 / 推断 / 设计选择 / 建议 / 待确认问题”，不要把低确定性的内容升级成高确定性的硬约束。对于推断、设计选择、建议和待确认问题，不要写进 PRD 的 1-6 章正文承诺区；如果需要保留，只写入 Source Metadata 对应字段。尤其是 `3.2 可选增强` 只允许保留已经确定的 optional capability；没有就留空，不要把建议项塞进去。尤其是页面加载时间、响应延迟、FPS、吞吐量、内存占用等量化性能指标：除非它们已经由 hard.* 或 Contract Metadata.validation.* 明确提供，否则正文、Product Contract 和验收标准里都必须保持定性表达，不得自行发明数值阈值。"
                        : stageSpecific + " 请显式区分“用户明确要求 / 上游事实 / 推断 / 设计选择 / 建议 / 待确认问题”，不要把低确定性的内容升级成高确定性的硬约束。对于推断、设计选择、建议和待确认问题，在正文中使用明确标签，例如“推断：”“设计选择：”“建议：”“待确认问题：”，不要把它们写成无来源的硬约束句式。如果某个具体细节缺少来源支撑，且并非推进当前阶段所必需，请优先省略它，而不是发明一个低权重但会污染下游的建议项。尤其是页面加载时间、响应延迟、FPS、吞吐量、内存占用等量化性能指标：除非它们已经由 hard.* 或 Contract Metadata.validation.* 明确提供，否则正文、Product Contract 和验收标准里都必须保持定性表达，不得自行发明数值阈值。",
                stageType == StageType.PRD
                        ? stageSpecific + " Explicitly distinguish user requirements, upstream facts, inferences, design decisions, recommendations, and open questions; do not promote low-certainty content into higher-certainty hard constraints. For the PRD stage, inferences, design choices, recommendations, and open questions must not live inside sections 1-6 as body commitments; if they need to be retained, store them only in the corresponding Source Metadata fields. In particular, section 3.2 may contain only settled optional capabilities; leave it empty rather than filling it with recommendation prose. Quantified performance metrics such as page-load time, response latency, FPS, throughput, or memory limits must remain qualitative unless they are explicitly provided by hard.* metadata or Contract Metadata.validation.*; do not invent numeric thresholds in the body, Product Contract, or acceptance criteria."
                        : stageSpecific + " Explicitly distinguish between user requirements, upstream facts, inferences, design decisions, recommendations, and open questions; do not promote low-certainty content into high-certainty hard constraints. When writing inferences, design choices, recommendations, or open questions, label them explicitly in the body (for example: “Inference:”, “Design Choice:”, “Recommendation:”, “Open Question:”) instead of phrasing them like source-backed hard constraints. If a concrete detail lacks source support and is not required to advance the current stage, prefer omitting it rather than inventing a low-authority recommendation that will pollute downstream stages. In particular, quantified performance metrics such as page-load time, response latency, FPS, throughput, or memory limits must remain qualitative unless they are explicitly provided by hard.* metadata or Contract Metadata.validation.*; do not invent numeric thresholds in the body, Product Contract, or acceptance criteria."
        );
    }

    String contractMetadataRequirement(DocumentLanguage language, int sectionNumber, String acceptanceSignalExample) {
        String heading = ArtifactLabels.numberedHeading(sectionNumber, ArtifactLabels.contractMetadata(language));
        String keyLines = """
                - %s: true|false
                - %s: html-entry | main-script | main-class | command | http-endpoint | importable-api | unspecified
                - %s: self-contained-entry | entry-with-local-dependencies | not-applicable
                - %s: entry-owned | companion-owned | not-applicable
                - %s: true|false
                - %s: true|false
                - %s: 使用英文短标识，逗号分隔，例如 %s
                - %s: true|false
                - %s: 整数毫秒；未声明则留空
                - %s: 整数毫秒；未声明则留空
                """.formatted(
                ContractMetadataKeys.RUNTIME_ENTRY_REQUIRED,
                ContractMetadataKeys.RUNTIME_ENTRY_KIND,
                ContractMetadataKeys.RUNTIME_ENTRY_PACKAGING_MODE,
                ContractMetadataKeys.RUNTIME_RUNTIME_OWNERSHIP_MODE,
                ContractMetadataKeys.RUNTIME_LAUNCH_REQUIRED,
                ContractMetadataKeys.RUNTIME_SURFACE_REQUIRED,
                ContractMetadataKeys.RUNTIME_ACCEPTANCE_SIGNALS,
                acceptanceSignalExample,
                ContractMetadataKeys.VALIDATION_PERFORMANCE_MEASUREMENT_REQUIRED,
                ContractMetadataKeys.VALIDATION_PAGE_LOAD_MAX_MS,
                ContractMetadataKeys.VALIDATION_INTERACTION_MAX_MS
        ).trim();
        return language.choose(
                "必须填写“%s”章节，并严格使用以下英文键：\n%s".formatted(heading, keyLines),
                "You must fill the “%s” section using these exact English keys:\n%s".formatted(heading, keyLines)
        );
    }

    String contractMetadataRuntimeKeyReminder(DocumentLanguage language) {
        String zhKeys = String.join("、",
                ContractMetadataKeys.RUNTIME_ENTRY_REQUIRED,
                ContractMetadataKeys.RUNTIME_ENTRY_KIND,
                ContractMetadataKeys.RUNTIME_ENTRY_PACKAGING_MODE,
                ContractMetadataKeys.RUNTIME_RUNTIME_OWNERSHIP_MODE,
                ContractMetadataKeys.RUNTIME_LAUNCH_REQUIRED,
                ContractMetadataKeys.RUNTIME_SURFACE_REQUIRED,
                ContractMetadataKeys.RUNTIME_ACCEPTANCE_SIGNALS
        );
        String enKeys = String.join(", ",
                ContractMetadataKeys.RUNTIME_ENTRY_REQUIRED,
                ContractMetadataKeys.RUNTIME_ENTRY_KIND,
                ContractMetadataKeys.RUNTIME_LAUNCH_REQUIRED,
                ContractMetadataKeys.RUNTIME_SURFACE_REQUIRED,
                ContractMetadataKeys.RUNTIME_ACCEPTANCE_SIGNALS
        );
        return language.choose(
                "如果需要补“%s”章节，必须严格使用这些英文键：%s".formatted(
                        ArtifactLabels.contractMetadata(language),
                        zhKeys
                ),
                "If the %s section needs to be added, it must use these exact English keys: %s".formatted(
                        ArtifactLabels.contractMetadata(language),
                        enKeys
                )
        );
    }
}
