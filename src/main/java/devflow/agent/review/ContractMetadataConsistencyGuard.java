package devflow.agent.review;

import devflow.agent.context.ContractMetadataKeys;
import devflow.agent.context.ContractRuntimeOwnershipMode;
import devflow.agent.context.EntryPackagingMode;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ExecutionEntryKind;
import devflow.agent.i18n.ArtifactLabels;
import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.protocol.ArtifactBlockKind;
import devflow.agent.protocol.StructuredArtifactBlocks;
import java.util.List;
import java.util.Map;

/**
 * 负责 Contract Metadata 自洽性检查。
 *
 * <p>这层只检查 runtime metadata 内部是否前后一致，
 * 不再从正文 prose 中猜额外语义。
 */
final class ContractMetadataConsistencyGuard {

    String detect(String candidateContent) {
        ExecutionContract structuredExecutionContract = StructuredArtifactBlocks.readFirstJsonBlock(
                candidateContent,
                ArtifactBlockKind.EXECUTION_CONTRACT,
                ExecutionContract.class
        );
        if (structuredExecutionContract != null) {
            return detectStructured(structuredExecutionContract);
        }
        String sectionBody = ReviewMarkdownSupport.extractSectionByTitle(candidateContent, ArtifactLabels.contractMetadata(DocumentLanguage.EN));
        if (sectionBody.isBlank()) {
            return null;
        }
        Map<String, String> metadata = ReviewMarkdownSupport.parseMetadataSection(sectionBody);
        boolean hasRuntimeMetadata = ContractMetadataKeys.runtimeKeys().stream().anyMatch(metadata::containsKey);
        if (!hasRuntimeMetadata) {
            return null;
        }
        ExecutionContract raw = new ExecutionContract(
                parseBoolean(metadata.get(ContractMetadataKeys.RUNTIME_ENTRY_REQUIRED), false),
                blank(metadata.get(ContractMetadataKeys.RUNTIME_ENTRY_KIND)),
                blank(metadata.get(ContractMetadataKeys.RUNTIME_ENTRY_PACKAGING_MODE)),
                blank(metadata.get(ContractMetadataKeys.RUNTIME_RUNTIME_OWNERSHIP_MODE)),
                parseBoolean(metadata.get(ContractMetadataKeys.RUNTIME_LAUNCH_REQUIRED), false),
                parseBoolean(metadata.get(ContractMetadataKeys.RUNTIME_SURFACE_REQUIRED), false),
                parseList(metadata.get(ContractMetadataKeys.RUNTIME_ACCEPTANCE_SIGNALS))
        );
        return detectStructured(raw);
    }

    private String detectStructured(ExecutionContract raw) {
        ExecutionContract normalized = raw.normalized();
        if (raw.entryRequired() != normalized.entryRequired()) {
            return "%s 已声明具体入口类型，但 %s 与其语义不一致。".formatted(
                    ContractMetadataKeys.RUNTIME_ENTRY_KIND,
                    ContractMetadataKeys.RUNTIME_ENTRY_REQUIRED
            );
        }
        if (normalized.normalizedEntryPackagingModeEnum() == devflow.agent.context.EntryPackagingMode.SELF_CONTAINED_ENTRY
                && normalized.normalizedRuntimeOwnershipModeEnum() == devflow.agent.context.ContractRuntimeOwnershipMode.COMPANION_OWNED) {
            return "%s=SELF_CONTAINED_ENTRY 与 %s=COMPANION_OWNED 不能同时成立。".formatted(
                    ContractMetadataKeys.RUNTIME_ENTRY_PACKAGING_MODE,
                    ContractMetadataKeys.RUNTIME_RUNTIME_OWNERSHIP_MODE
            );
        }
        if (normalized.normalizedEntryKindEnum() == ExecutionEntryKind.HTML_ENTRY
                && normalized.normalizedEntryPackagingModeEnum() == EntryPackagingMode.ENTRY_WITH_LOCAL_DEPENDENCIES
                && normalized.normalizedRuntimeOwnershipModeEnum() == ContractRuntimeOwnershipMode.ENTRY_OWNED) {
            return "%s=ENTRY_WITH_LOCAL_DEPENDENCIES 时，%s 不能收紧为 ENTRY_OWNED；普通网页入口默认应使用 NOT_APPLICABLE，只有入口自包含时才允许 SELF_CONTAINED_ENTRY + ENTRY_OWNED。".formatted(
                    ContractMetadataKeys.RUNTIME_ENTRY_PACKAGING_MODE,
                    ContractMetadataKeys.RUNTIME_RUNTIME_OWNERSHIP_MODE
            );
        }
        if (raw.launchRequired() != normalized.launchRequired()) {
            return "%s 与 %s / %s 的语义不一致。".formatted(
                    ContractMetadataKeys.RUNTIME_LAUNCH_REQUIRED,
                    ContractMetadataKeys.RUNTIME_ENTRY_KIND,
                    ContractMetadataKeys.RUNTIME_ACCEPTANCE_SIGNALS
            );
        }
        if (raw.surfaceRequired() != normalized.surfaceRequired()) {
            return "%s 与 %s / %s 的语义不一致。".formatted(
                    ContractMetadataKeys.RUNTIME_SURFACE_REQUIRED,
                    ContractMetadataKeys.RUNTIME_ENTRY_KIND,
                    ContractMetadataKeys.RUNTIME_ACCEPTANCE_SIGNALS
            );
        }
        return null;
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank() || PlaceholderValues.isNoneLiteral(value)) {
            return List.of();
        }
        return splitCommaSeparatedValues(value);
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private List<String> splitCommaSeparatedValues(String value) {
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == ',' || ch == '，') {
                appendSplitValue(parts, current);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        appendSplitValue(parts, current);
        return List.copyOf(parts);
    }

    private void appendSplitValue(java.util.List<String> target, StringBuilder current) {
        String normalized = current.toString().trim();
        if (!normalized.isBlank()) {
            target.add(normalized);
        }
    }
}
