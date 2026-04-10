package devflow.agent.context;

import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import devflow.agent.markdown.MarkdownSectionScanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contract metadata 和 source metadata 读取器。
 *
 * <p>它只负责读取结构化 block 或 markdown metadata section，
 * 不负责 Product/Design/Execution contract 的上层装配。
 */
final class ContractMetadataReader {

    private final ContractSectionResolver sectionResolver;
    private final ContractListSupport listSupport;

    ContractMetadataReader(ContractSectionResolver sectionResolver, ContractListSupport listSupport) {
        this.sectionResolver = sectionResolver;
        this.listSupport = listSupport;
    }

    Map<String, String> parseContractMetadataSection(String markdown) {
        return parseMetadataSection(markdown, ArtifactLabels.contractMetadata(devflow.agent.i18n.DocumentLanguage.EN));
    }

    ValidationMetadata extractValidationMetadata(String markdown) {
        ValidationMetadata structuredValidationMetadata = StructuredArtifactBlocks.readFirstJsonBlock(
                markdown,
                ArtifactBlockKind.VALIDATION_METADATA,
                ValidationMetadata.class
        );
        if (structuredValidationMetadata != null) {
            return structuredValidationMetadata;
        }
        Map<String, String> metadata = parseContractMetadataSection(markdown);
        if (metadata.isEmpty()) {
            return ValidationMetadata.empty();
        }
        return new ValidationMetadata(
                listSupport.parseBoolean(metadata.get(ContractMetadataKeys.VALIDATION_PERFORMANCE_MEASUREMENT_REQUIRED), false),
                listSupport.parseInteger(metadata.get(ContractMetadataKeys.VALIDATION_PAGE_LOAD_MAX_MS)),
                listSupport.parseInteger(metadata.get(ContractMetadataKeys.VALIDATION_INTERACTION_MAX_MS))
        );
    }

    ConstraintSourceMetadata extractSourceMetadata(String markdown) {
        ConstraintSourceMetadata structuredSourceMetadata = StructuredArtifactBlocks.readFirstJsonBlock(
                markdown,
                ArtifactBlockKind.SOURCE_METADATA,
                ConstraintSourceMetadata.class
        );
        if (structuredSourceMetadata != null) {
            return structuredSourceMetadata;
        }
        Map<String, String> metadata = parseMetadataSection(
                markdown,
                ArtifactLabels.sourceMetadata(devflow.agent.i18n.DocumentLanguage.EN)
        );
        return new ConstraintSourceMetadata(
                listSupport.parseList(metadata.get(SourceMetadataKeys.HARD_USER_REQUIREMENTS)),
                listSupport.parseList(metadata.get(SourceMetadataKeys.HARD_UPSTREAM_FACTS)),
                listSupport.parseList(metadata.get(SourceMetadataKeys.SOFT_INFERENCES)),
                listSupport.parseList(metadata.get(SourceMetadataKeys.SOFT_DESIGN_DECISIONS)),
                listSupport.parseList(metadata.get(SourceMetadataKeys.SOFT_RECOMMENDATIONS)),
                listSupport.parseList(metadata.get(SourceMetadataKeys.OPEN_QUESTIONS))
        );
    }

    ConstraintSourceMetadata mergeSourceMetadata(ConstraintSourceMetadata... values) {
        ConstraintSourceMetadata merged = new ConstraintSourceMetadata(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        if (values == null) {
            return merged;
        }
        for (ConstraintSourceMetadata value : values) {
            if (value == null) {
                continue;
            }
            merged = merged.merge(value);
        }
        return merged;
    }

    ConstraintSourceMetadata[] extractFromMany(String... markdownValues) {
        if (markdownValues == null || markdownValues.length == 0) {
            return new ConstraintSourceMetadata[0];
        }
        List<ConstraintSourceMetadata> collected = new ArrayList<>();
        for (String markdownValue : markdownValues) {
            if (markdownValue == null || markdownValue.isBlank()) {
                continue;
            }
            collected.add(extractSourceMetadata(markdownValue));
        }
        return collected.toArray(ConstraintSourceMetadata[]::new);
    }

    private Map<String, String> parseMetadataSection(String markdown, String headingTitle) {
        if (markdown == null || markdown.isBlank()) {
            return Map.of();
        }
        String sectionBody = sectionResolver.sectionByTitle(markdown, headingTitle);
        if (sectionBody.isBlank()) {
            return Map.of();
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        MarkdownSectionScanner.parseMetadataLines(sectionBody).forEach(metadata::put);
        return metadata;
    }
}
