package devflow.agent.artifact;

import devflow.agent.context.ConstraintSourceMetadata;
import devflow.agent.context.ContractExtractor;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ProductContract;
import devflow.agent.context.ValidationMetadata;
import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.orchestrator.StageType;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import java.util.EnumSet;

/**
 * 文档阶段生成后的本地稳定化处理。
 *
 * <p>负责把模型草稿收束成流程可消费的稳定产物：
 * source metadata、contract metadata、结构化 block 和约束净化都在这里完成。
 */
final class DocumentStagePostProcessor {

    private static final EnumSet<ArtifactSectionKind> TRANSIENT_CANONICAL_SECTIONS =
            EnumSet.of(ArtifactSectionKind.CURRENT_NOTES);

    private final ContractExtractor contractExtractor;
    private final DocumentDraftAssembler draftAssembler;
    private final ContractMetadataSectionRenderer contractMetadataSectionRenderer;
    private final PrdLowAuthorityContentCanonicalizer prdLowAuthorityContentCanonicalizer;
    private final PrdQuantitativeConstraintCanonicalizer prdQuantitativeConstraintCanonicalizer;

    DocumentStagePostProcessor(ContractExtractor contractExtractor, DocumentDraftAssembler draftAssembler) {
        this.contractExtractor = contractExtractor;
        this.draftAssembler = draftAssembler;
        this.contractMetadataSectionRenderer = new ContractMetadataSectionRenderer();
        this.prdLowAuthorityContentCanonicalizer = new PrdLowAuthorityContentCanonicalizer(draftAssembler);
        this.prdQuantitativeConstraintCanonicalizer = new PrdQuantitativeConstraintCanonicalizer();
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

    String stabilizeContractMetadata(
            String markdown,
            ExecutionContract executionContract,
            ValidationMetadata validationMetadata,
            int sectionNumber
    ) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        return draftAssembler.replaceTitledSection(
                markdown,
                ArtifactLabels.contractMetadata(DocumentLanguage.EN),
                contractMetadataSectionRenderer.render(sectionNumber, executionContract, validationMetadata)
        );
    }

    String sanitizeDocumentConstraintEscalation(
            StageType stageType,
            String markdown,
            ConstraintSourceMetadata authoritativeSourceMetadata,
            ValidationMetadata validationMetadata,
            DocumentLanguage language
    ) {
        String canonicalMarkdown = stripTransientCanonicalSections(markdown);
        if (canonicalMarkdown == null || canonicalMarkdown.isBlank()) {
            return markdown;
        }
        if (stageType == StageType.PRD) {
            String lowAuthorityCanonicalized = prdLowAuthorityContentCanonicalizer.canonicalize(
                    canonicalMarkdown,
                    contractExtractor.extractConstraintSourceMetadata(StructuredArtifactBlocks.stripAllKnownBlocks(canonicalMarkdown)),
                    language
            );
            return prdQuantitativeConstraintCanonicalizer.canonicalize(
                    lowAuthorityCanonicalized,
                    authoritativeSourceMetadata,
                    validationMetadata,
                    language
            );
        }
        return canonicalMarkdown.trim() + "\n";
    }

    String stripMachineBlocks(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        return StructuredArtifactBlocks.stripAllKnownBlocks(markdown).trim() + "\n";
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

    private String stripTransientCanonicalSections(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        return ArtifactSectionSupport.removeSections(markdown, TRANSIENT_CANONICAL_SECTIONS);
    }
}
