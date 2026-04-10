package devflow.agent.executor;

import devflow.agent.context.ContractView;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.ProductContract;
import devflow.agent.context.RequirementReference;
import devflow.agent.quality.CapabilitySurface;
import devflow.agent.quality.QualityCoverageRefCatalog;
import devflow.agent.quality.QualityPlan;
import devflow.agent.util.ProjectPathSupport;
import devflow.agent.validation.ProjectFingerprint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

public class ImplementationPlanCoverageAnalyzer {

    private final QualityChecklistCoverageAnalyzer qualityChecklistCoverageAnalyzer = new QualityChecklistCoverageAnalyzer();

    public CoverageResult analyze(
            ProjectFingerprint fingerprint,
            ContractView contractView,
            List<String> plannedPaths,
            List<String> plannedCoverageItems,
            List<String> plannedCoverageRefs,
            List<String> plannedDeliveryModes,
            boolean hasRunnableMilestone,
            boolean hasNonSkeletonRunnableMilestone,
            QualityPlan qualityPlan
    ) {
        ExecutionContract executionContract = contractView == null ? null : contractView.executionContract();
        if (executionContract == null || !executionContract.entryRequired()) {
            return CoverageResult.success();
        }
        List<String> normalizedPaths = plannedPaths == null ? List.of() : plannedPaths;
        List<String> normalizedModes = plannedDeliveryModes == null ? List.of() : plannedDeliveryModes;
        List<String> issues = new ArrayList<>();
        boolean hasExistingEntry = hasResolvableEntry(fingerprint, executionContract);
        boolean plansEntry = normalizedPaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(path -> path.trim().toLowerCase())
                .anyMatch(executionContract.normalizedEntryKindEnum()::matchesProjectPath);
        if (!hasExistingEntry && !plansEntry) {
            issues.add("执行契约要求提供可启动入口，但当前计划没有覆盖入口文件或入口接线。");
        }
        if (executionContract.surfaceRequired() && !coversSurface(normalizedPaths, executionContract, hasExistingEntry)) {
            issues.add("执行契约要求提供可见运行表面，但当前计划只覆盖内部模块，没有覆盖入口或运行表面。");
        }
        if (requiresBehaviorFill(executionContract) && planStopsAtSkeleton(normalizedModes)) {
            issues.add("当前实现计划只建立可运行骨架，没有安排后续行为填充或集成子任务。");
        }
        if (requiresBehaviorFill(executionContract) && !hasRunnableMilestone) {
            issues.add("当前实现计划没有明确的可运行里程碑子任务，缺少把交付物真正接成可启动状态的责任归属。");
        }
        if (requiresBehaviorFill(executionContract) && hasRunnableMilestone && !hasNonSkeletonRunnableMilestone && planStopsAtSkeleton(normalizedModes)) {
            issues.add("当前实现计划把可运行里程碑停留在骨架层，没有安排非 SKELETON 的接线或行为补齐步骤。");
        }
        if (executionContract.surfaceRequired()) {
            issues.addAll(detectMissingCapabilityCoverage(
                    contractView == null ? null : contractView.productContract(),
                    plannedCoverageRefs,
                    plannedCoverageItems
            ));
        }
        issues.addAll(qualityChecklistCoverageAnalyzer.detectMissingRequiredCoverageRefs(qualityPlan, plannedCoverageRefs));
        return issues.isEmpty()
                ? CoverageResult.success()
                : CoverageResult.failure("当前实现计划未覆盖执行契约。", issues);
    }

    /**
     * 检查 runnable milestone 的子任务结构是否自洽。
     *
     * <p>这里的职责只到“是否存在承担入口责任的里程碑子任务”为止，
     * 不再把单文件、多文件、内联脚本或外部脚本模块这些实现组织方式升级成 hard gate。
     * 这些属于实现选择，应交给 planner、coder 和后续验证链，而不是在 planning gate 里写死。
     */
    public CoverageResult analyzeRunnableMilestones(
            ContractView contractView,
            List<Subtask> subtasks
    ) {
        ExecutionContract executionContract = contractView == null ? null : contractView.executionContract();
        if (executionContract == null || subtasks == null || subtasks.isEmpty()) {
            return CoverageResult.success();
        }
        if (!executionContract.requiresHtmlEntry()) {
            return CoverageResult.success();
        }
        List<String> issues = new ArrayList<>();
        for (Subtask subtask : subtasks) {
            if (subtask == null || !subtask.runnableMilestone()) {
                continue;
            }
            List<FileChange> changes = subtask.changes() == null ? List.of() : subtask.changes();
            boolean touchesHtmlEntry = changes.stream()
                    .map(FileChange::path)
                    .filter(path -> path != null && !path.isBlank())
                    .map(path -> path.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(ProjectPathSupport::isHtml);
            if (!touchesHtmlEntry) {
                issues.add("可运行里程碑子任务必须直接覆盖 HTML 入口文件，不能只改内部逻辑模块。");
            }
        }
        return issues.isEmpty()
                ? CoverageResult.success()
                : CoverageResult.failure("当前 runnable milestone 结构与执行契约不一致。", issues);
    }

    private List<String> detectMissingCapabilityCoverage(
            ProductContract productContract,
            List<String> plannedCoverageRefs,
            List<String> plannedCoverageItems
    ) {
        if (productContract == null) {
            return List.of();
        }
        List<RequirementReference> requiredItems = productContract.planningCoverageRequirements();
        if (requiredItems.isEmpty()) {
            return List.of();
        }
        List<String> normalizedRefs = plannedCoverageRefs == null
                ? List.of()
                : plannedCoverageRefs.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(this::normalizeCoverageRef)
                .distinct()
                .toList();
        if (normalizedRefs.isEmpty()) {
            String planCorpus = String.join("\n", plannedCoverageItems == null ? List.of() : plannedCoverageItems);
            if (planCorpus.isBlank()) {
                return List.of("当前实现计划没有给出任何 coverageRefs，也没有描述直接实现能力的覆盖信息，无法证明其覆盖本阶段应实现的产品能力。");
            }
            return List.of("当前实现计划缺少结构化 coverageRefs，无法稳定证明其覆盖本阶段应实现的直接产品能力。");
        }
        List<String> issues = new ArrayList<>();
        for (RequirementReference item : requiredItems) {
            if (normalizedRefs.contains(normalizeCoverageRef(item.id()))) {
                continue;
            }
            issues.add("当前实现计划未覆盖产品要求 " + item.id() + "：" + trimForIssue(item.text()));
            if (issues.size() >= 3) {
                break;
            }
        }
        return issues;
    }
    private String normalizeCoverageRef(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimForIssue(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "...";
    }

    private boolean requiresBehaviorFill(ExecutionContract executionContract) {
        if (executionContract == null) {
            return false;
        }
        return executionContract.launchRequired()
                || executionContract.surfaceRequired()
                || (executionContract.acceptanceSignals() != null && !executionContract.acceptanceSignals().isEmpty());
    }

    private boolean planStopsAtSkeleton(List<String> plannedDeliveryModes) {
        if (plannedDeliveryModes == null || plannedDeliveryModes.isEmpty()) {
            return false;
        }
        List<DeliveryMode> modes = plannedDeliveryModes.stream()
                .map(mode -> devflow.agent.util.EnumParsers.parseIgnoreCase(DeliveryMode.class, mode, DeliveryMode.INCREMENTAL))
                .toList();
        int firstSkeletonIndex = IntStream.range(0, modes.size())
                .filter(index -> modes.get(index) == DeliveryMode.SKELETON)
                .findFirst()
                .orElse(-1);
        if (firstSkeletonIndex < 0) {
            return false;
        }
        boolean hasFollowUpImplementation = IntStream.range(firstSkeletonIndex + 1, modes.size())
                .mapToObj(modes::get)
                .anyMatch(mode -> mode != DeliveryMode.SKELETON);
        if (!hasFollowUpImplementation) {
            return true;
        }
        return modes.get(modes.size() - 1) == DeliveryMode.SKELETON;
    }

    private boolean coversSurface(List<String> plannedPaths, ExecutionContract executionContract, boolean hasExistingEntry) {
        if (hasExistingEntry) {
            return true;
        }
        return plannedPaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(path -> path.trim().toLowerCase())
                .anyMatch(path -> executionContract.normalizedEntryKindEnum().matchesProjectPath(path)
                        || ProjectPathSupport.isHtml(path)
                        || ProjectPathSupport.isStyle(path));
    }

    private boolean hasResolvableEntry(ProjectFingerprint fingerprint, ExecutionContract executionContract) {
        if (fingerprint == null || executionContract == null || !executionContract.entryRequired()) {
            return false;
        }
        if (executionContract.requiresHtmlEntry()) {
            return fingerprint.hasResolvedHtmlEntry();
        }
        return fingerprint.fileNames().stream()
                .map(path -> path == null ? "" : path.toLowerCase())
                .anyMatch(executionContract.normalizedEntryKindEnum()::matchesProjectPath);
    }
}
