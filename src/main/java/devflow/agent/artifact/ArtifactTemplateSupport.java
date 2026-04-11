package devflow.agent.artifact;

import devflow.agent.context.ContractMetadataKeys;
import devflow.agent.context.SourceMetadataKeys;
import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import java.time.Instant;

/**
 * 阶段模板共享支持。
 *
 * <p>统一收纳：
 * 1. 时间戳与标题标签；
 * 2. Contract Metadata / Source Metadata 模板块；
 * 3. 常用 heading 生成。
 */
final class ArtifactTemplateSupport {

    String generatedAtLabel(DocumentLanguage language) {
        return ArtifactLabels.generatedAt(language);
    }

    String now() {
        return Instant.now().toString();
    }

    String contractMetadataTemplateBlock() {
        return """
                - %s:
                - %s:
                - %s:
                - %s:
                - %s:
                - %s:
                - %s:
                - %s:
                """.formatted(
                ContractMetadataKeys.RUNTIME_ENTRY_REQUIRED,
                ContractMetadataKeys.RUNTIME_ENTRY_KIND,
                ContractMetadataKeys.RUNTIME_LAUNCH_REQUIRED,
                ContractMetadataKeys.RUNTIME_SURFACE_REQUIRED,
                ContractMetadataKeys.RUNTIME_ACCEPTANCE_SIGNALS,
                ContractMetadataKeys.VALIDATION_PERFORMANCE_MEASUREMENT_REQUIRED,
                ContractMetadataKeys.VALIDATION_PAGE_LOAD_MAX_MS,
                ContractMetadataKeys.VALIDATION_INTERACTION_MAX_MS
        ).trim();
    }

    String sourceMetadataTemplateBlock() {
        return SourceMetadataKeys.allKeys().stream()
                .map(SourceMetadataKeys::markdownLinePrefix)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    String numberedSourceMetadataHeading(int sectionNumber, DocumentLanguage language) {
        return ArtifactLabels.numberedHeading(sectionNumber, ArtifactLabels.sourceMetadata(language));
    }

    String numberedContractMetadataHeading(int sectionNumber, DocumentLanguage language) {
        return ArtifactLabels.numberedHeading(sectionNumber, ArtifactLabels.contractMetadata(language));
    }
}
