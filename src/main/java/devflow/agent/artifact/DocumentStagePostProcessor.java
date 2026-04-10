package devflow.agent.artifact;

import devflow.agent.context.ConstraintAuthoritySupport;
import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ContractMetadataKeys;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ProductContract;
import devflow.agent.context.SourceMetadataKeys;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档阶段生成后的本地稳定化处理。
 *
 * <p>负责把模型草稿收束成流程可消费的稳定产物：
 * source metadata、contract metadata、结构化 block 和约束净化都在这里完成。
 */
final class DocumentStagePostProcessor {

    private final ContractExtractor contractExtractor;
    private final DocumentDraftAssembler draftAssembler;

    DocumentStagePostProcessor(ContractExtractor contractExtractor, DocumentDraftAssembler draftAssembler) {
        this.contractExtractor = contractExtractor;
        this.draftAssembler = draftAssembler;
    }

    String stabilizeSourceMetadata(
            String markdown,
            ConstraintSourceMetadata authoritativeSourceMetadata,
            int sectionNumber,
            DocumentLanguage language
    ) {
        ConstraintSourceMetadata generatedMetadata = contractExtractor.extractConstraintSourceMetadata(markdown);
        ConstraintSourceMetadata stabilized = new ConstraintSourceMetadata(
                authoritativeSourceMetadata.hardUserRequirements(),
                authoritativeSourceMetadata.hardUpstreamFacts(),
                generatedMetadata.softInferences(),
                generatedMetadata.softDesignDecisions(),
                generatedMetadata.softRecommendations(),
                generatedMetadata.openQuestions()
        );
        return draftAssembler.replaceTitledSection(
                markdown,
                ArtifactLabels.sourceMetadata(language),
                stabilized.toMarkdown(sectionNumber, language)
        );
    }

    String stabilizeExecutionContractMetadata(
            String markdown,
            ExecutionContract executionContract,
            int sectionNumber
    ) {
        if (markdown == null || markdown.isBlank() || executionContract == null) {
            return markdown;
        }
        return draftAssembler.replaceTitledSection(
                markdown,
                ArtifactLabels.contractMetadata(DocumentLanguage.EN),
                executionContract.normalized().toMetadataSectionMarkdown(sectionNumber)
        );
    }

    String sanitizeDocumentConstraintEscalation(
            RunRecord runRecord,
            StageType stageType,
            String markdown,
            ConstraintSourceMetadata authoritativeSourceMetadata,
            ExecutionContract executionContract,
            DocumentLanguage language
    ) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        String authorityCorpus = ConstraintAuthoritySupport.buildAuthorityCorpus(
                runRecord.goal(),
                runRecord.constraints(),
                authoritativeSourceMetadata,
                executionContract
        );
        List<DocumentSectionBlock> sections = draftAssembler.parseTopLevelSections(markdown);
        if (sections.isEmpty()) {
            return markdown;
        }
        StringBuilder rebuilt = new StringBuilder();
        int cursor = 0;
        for (DocumentSectionBlock section : sections) {
            rebuilt.append(markdown, cursor, section.start());
            String raw = section.raw();
            rebuilt.append(shouldSkipConstraintSanitization(raw)
                    ? raw
                    : sanitizeSectionContent(raw, stageType, authorityCorpus, language));
            cursor = section.end();
        }
        rebuilt.append(markdown.substring(cursor));
        return rebuilt.toString().trim() + "\n";
    }

    String upsertDocumentBlocks(
            String markdown,
            ConstraintSourceMetadata sourceMetadata,
            ProductContract productContract,
            ExecutionContract executionContract,
            ValidationMetadata validationMetadata
    ) {
        String rendered = markdown == null ? "" : markdown;
        if (sourceMetadata != null) {
            rendered = StructuredArtifactBlocks.upsertJsonBlock(rendered, ArtifactBlockKind.SOURCE_METADATA, sourceMetadata);
        }
        if (productContract != null) {
            rendered = StructuredArtifactBlocks.upsertJsonBlock(rendered, ArtifactBlockKind.PRODUCT_CONTRACT, productContract);
        }
        if (executionContract != null) {
            rendered = StructuredArtifactBlocks.upsertJsonBlock(rendered, ArtifactBlockKind.EXECUTION_CONTRACT, executionContract.normalized());
        }
        if (validationMetadata != null) {
            rendered = StructuredArtifactBlocks.upsertJsonBlock(rendered, ArtifactBlockKind.VALIDATION_METADATA, validationMetadata);
        }
        return rendered;
    }

    private boolean shouldSkipConstraintSanitization(String rawSection) {
        String firstLine = rawSection == null ? "" : rawSection.lines().findFirst().orElse("");
        ArtifactSectionKind sectionKind = ArtifactSectionSupport.classifySecondLevelHeading(firstLine);
        return sectionKind == ArtifactSectionKind.SOURCE_METADATA
                || sectionKind == ArtifactSectionKind.CONTRACT_METADATA
                || sectionKind == ArtifactSectionKind.CURRENT_NOTES;
    }

    private String sanitizeSectionContent(
            String rawSection,
            StageType stageType,
            String authorityCorpus,
            DocumentLanguage language
    ) {
        int lineBreak = rawSection.indexOf('\n');
        if (lineBreak < 0) {
            return rawSection;
        }
        String heading = rawSection.substring(0, lineBreak);
        String body = rawSection.substring(lineBreak + 1);
        List<String> rewrittenLines = new ArrayList<>();
        /*
         * 这里不能用 String.lines()。
         *
         * String.lines() 会丢掉结尾的空行，而顶层章节之间本来就依赖这些空行来保持
         * heading 分隔；一旦在净化阶段吞掉结尾空行，后续章节标题会直接贴在上一节正文后面。
         */
        for (String line : body.split("\n", -1)) {
            rewrittenLines.add(sanitizeConstraintLine(line));
        }
        return heading + "\n" + String.join("\n", rewrittenLines);
    }

    private String sanitizeConstraintLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isBlank()
                || trimmed.startsWith("#")
                || trimmed.startsWith(SourceMetadataKeys.markdownLinePrefix(SourceMetadataKeys.HARD_USER_REQUIREMENTS))
                || trimmed.startsWith(SourceMetadataKeys.markdownLinePrefix(SourceMetadataKeys.HARD_UPSTREAM_FACTS))
                || trimmed.startsWith(SourceMetadataKeys.markdownLinePrefix(SourceMetadataKeys.SOFT_INFERENCES))
                || trimmed.startsWith(SourceMetadataKeys.markdownLinePrefix(SourceMetadataKeys.SOFT_DESIGN_DECISIONS))
                || trimmed.startsWith(SourceMetadataKeys.markdownLinePrefix(SourceMetadataKeys.SOFT_RECOMMENDATIONS))
                || trimmed.startsWith(SourceMetadataKeys.markdownLinePrefix(SourceMetadataKeys.OPEN_QUESTIONS))
                || trimmed.startsWith("- " + ContractMetadataKeys.RUNTIME_ENTRY_REQUIRED + ":")
                || trimmed.startsWith("- " + ContractMetadataKeys.RUNTIME_ENTRY_KIND + ":")
                || trimmed.startsWith("- " + ContractMetadataKeys.RUNTIME_LAUNCH_REQUIRED + ":")
                || trimmed.startsWith("- " + ContractMetadataKeys.RUNTIME_SURFACE_REQUIRED + ":")
                || trimmed.startsWith("- " + ContractMetadataKeys.RUNTIME_ACCEPTANCE_SIGNALS + ":")
                || trimmed.startsWith("[")) {
            return line;
        }
        return line;
    }
}
