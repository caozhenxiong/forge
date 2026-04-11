package devflow.agent.review;

import devflow.agent.context.ExecutionContract;
import devflow.agent.markdown.MarkdownSectionScanner;
import devflow.agent.orchestrator.RunRecord;
import devflow.agent.orchestrator.StageType;
import devflow.agent.text.TextCanonicalizer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档阶段的确定性结构护栏。
 *
 * <p>它负责：
 * 1. 必需章节完整性检查；
 * 2. 结构截断/污染检查；
 * 3. Contract Metadata 自洽性检查。
 *
 * <p>它不负责：
 * 1. 文档语义质量判断；
 * 2. reviewer 假阳性降噪；
 * 3. 从正文自然语言里猜“这是不是硬约束/设计选择/开放问题”；
 * 4. 阶段间开放问题的语义解释；
 * 5. 从 Product Contract prose 或 bullet 文本里推断性能语义。
 */
class DocumentStructureGuard {

    private final RequiredDocumentSectionPolicy sectionPolicy = new RequiredDocumentSectionPolicy();
    private final DocumentIntegrityGuardSupport documentIntegrityGuardSupport = new DocumentIntegrityGuardSupport();
    private final ContractMetadataConsistencyGuard contractMetadataConsistencyGuard = new ContractMetadataConsistencyGuard();

    ReviewResult enforce(
            RunRecord runRecord,
            StageType stageType,
            String candidateContent,
            String artifactLabel,
            ReviewResult baseResult
    ) {
        List<Integer> requiredSections = sectionPolicy.requiredSectionNumbers(stageType);
        List<String> invalidSections = requiredSections.stream()
                .filter(section -> sectionMissingOrEmpty(stageType, candidateContent, section))
                .map(section -> "## " + section + ".")
                .toList();
        if (!invalidSections.isEmpty()) {
            String missing = String.join("、", invalidSections);
            return new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                    artifactLabel + " 缺少规范章节：" + trimFinding(missing, 80),
                    "请补齐以下章节后重试：" + missing
            );
        }

        String integrityIssue = documentIntegrityGuardSupport.detect(stageType, candidateContent, requiredSections);
        if (integrityIssue != null) {
            return new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                    artifactLabel + " 存在结构完整性问题：" + integrityIssue,
                    "请修复文档结构问题后重试：" + integrityIssue
            );
        }

        String inconsistentExecutionContractMetadata = contractMetadataConsistencyGuard.detect(candidateContent);
        if (inconsistentExecutionContractMetadata != null) {
            return new ReviewResult(
                    ReviewDecision.REVISION_REQUIRED,
                    FixMode.PATCH,
                    artifactLabel + " 的 Contract Metadata 不自洽：" + trimFinding(inconsistentExecutionContractMetadata, 80),
                    "请修复 Contract Metadata，使 entryKind、entryPackagingMode、runtimeOwnershipMode、entryRequired、launchRequired、surfaceRequired 和 acceptanceSignals 的语义保持一致：" + trimFinding(inconsistentExecutionContractMetadata, 120)
            );
        }

        return baseResult;
    }

    private boolean sectionMissingOrEmpty(StageType stageType, String candidateContent, int section) {
        String block = extractSectionBlock(candidateContent, section);
        if (block == null) {
            return true;
        }
        String withoutHeading = stripHeading(block).trim();
        if (withoutHeading.isBlank()) {
            return true;
        }
        if (sectionPolicy.isContractMetadataSection(stageType, section)) {
            Map<String, String> metadata = ReviewMarkdownSupport.parseMetadataSection(withoutHeading);
            return contractMetadataMissingKeys(metadata);
        }
        String meaningfulLines = withoutHeading.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !MarkdownSectionScanner.isHeadingLine(line))
                .collect(Collectors.joining("\n"));
        return meaningfulLines.isBlank();
    }

    private String extractSectionBlock(String content, int sectionNumber) {
        return MarkdownSectionScanner.scanSecondLevelSections(content).stream()
                .filter(section -> section.number() == sectionNumber)
                .map(MarkdownSectionScanner.Section::raw)
                .findFirst()
                .orElse(null);
    }

    private String stripHeading(String block) {
        if (block == null || block.isBlank()) {
            return "";
        }
        int newline = block.indexOf('\n');
        return newline < 0 ? "" : block.substring(newline + 1);
    }

    private boolean contractMetadataMissingKeys(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return true;
        }
        return devflow.agent.context.ContractMetadataKeys.runtimeKeys().stream().anyMatch(key -> !metadata.containsKey(key));
    }

    private String trimFinding(String finding, int limit) {
        String normalized = TextCanonicalizer.collapseWhitespace(finding);
        return normalized.length() > limit ? normalized.substring(0, limit) : normalized;
    }
}
