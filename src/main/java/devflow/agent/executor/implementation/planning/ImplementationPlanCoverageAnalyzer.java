package devflow.agent.executor.implementation.planning;
import devflow.agent.executor.*;
import devflow.agent.executor.implementation.*;
import devflow.agent.executor.implementation.state.*;
import devflow.agent.executor.implementation.render.*;
import devflow.agent.executor.implementation.toolloop.*;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.AuthoritativeCoverageCatalog;
import devflow.agent.context.ContractView;
import devflow.agent.context.ExecutionContract;
import devflow.agent.context.RequirementReference;
import devflow.agent.quality.QualityPlan;
import devflow.agent.util.ProjectPathSupport;
import devflow.agent.validation.ProjectFingerprint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import devflow.agent.executor.subtask.Subtask;
public class ImplementationPlanCoverageAnalyzer {

    public CoverageResult analyzeCapabilityPartition(List<Subtask> subtasks) {
        if (subtasks == null || subtasks.isEmpty()) {
            return CoverageResult.success();
        }
        return analyzeCapabilityPartitionUnits(
                subtasks.stream()
                        .map(subtask -> new CapabilityPartitionUnit(
                                subtask == null ? "" : blank(subtask.title()),
                                subtask == null ? List.of() : safeCapabilities(subtask.ownedCapabilities()),
                                subtask == null ? List.of() : safeCapabilities(subtask.deferredCapabilities()),
                                subtask == null ? List.of() : safePathsFromChanges(subtask.changes())
                        ))
                        .toList()
        );
    }

    public CoverageResult analyzeCapabilityPartitionOutline(List<ImplementationOutlineSubtask> subtasks) {
        if (subtasks == null || subtasks.isEmpty()) {
            return CoverageResult.success();
        }
        return analyzeCapabilityPartitionUnits(
                subtasks.stream()
                        .map(subtask -> new CapabilityPartitionUnit(
                                subtask == null ? "" : blank(subtask.id()).isBlank() ? blank(subtask.title()) : blank(subtask.id()),
                                subtask == null ? List.of() : safeCapabilities(subtask.ownedCapabilities()),
                                subtask == null ? List.of() : safeCapabilities(subtask.deferredCapabilities()),
                                subtask == null ? List.of() : safePaths(subtask.targetPaths())
                        ))
                        .toList()
        );
    }

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
        issues.addAll(detectMissingCapabilityCoverage(
                AuthoritativeCoverageCatalog.from(
                        contractView == null ? null : contractView.productContract(),
                        qualityPlan
                ),
                plannedCoverageRefs,
                plannedCoverageItems
        ));
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
            AuthoritativeCoverageCatalog authoritativeCoverageCatalog,
            List<String> plannedCoverageRefs,
            List<String> plannedCoverageItems
    ) {
        if (authoritativeCoverageCatalog == null) {
            return List.of();
        }
        List<RequirementReference> requiredItems = authoritativeCoverageCatalog.planningRequiredReferences();
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
            issues.add("当前实现计划未覆盖权威覆盖引用 " + item.id() + "：" + trimForIssue(item.text()));
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

    private CoverageResult analyzeCapabilityPartitionUnits(List<CapabilityPartitionUnit> units) {
        if (units == null || units.isEmpty()) {
            return CoverageResult.success();
        }
        List<String> issues = new ArrayList<>();
        Map<String, List<String>> ownedByCapability = new LinkedHashMap<>();
        for (CapabilityPartitionUnit unit : units) {
            LinkedHashSet<String> overlap = new LinkedHashSet<>(unit.ownedCapabilities());
            overlap.retainAll(unit.deferredCapabilities());
            if (!overlap.isEmpty()) {
                issues.add("子任务 " + renderUnitId(unit.id()) + " 的 ownedCapabilities 与 deferredCapabilities 不能重叠：" + String.join("、", overlap));
            }
            for (String capability : unit.ownedCapabilities()) {
                ownedByCapability.computeIfAbsent(capability, ignored -> new ArrayList<>()).add(renderUnitId(unit.id()));
            }
        }
        for (Map.Entry<String, List<String>> entry : ownedByCapability.entrySet()) {
            if (entry.getValue().size() > 1) {
                issues.add("能力 " + entry.getKey() + " 不能被多个子任务同时声明为 ownedCapabilities：" + String.join("、", entry.getValue()));
            }
        }
        for (int index = 0; index < units.size(); index++) {
            CapabilityPartitionUnit unit = units.get(index);
            for (String capability : unit.deferredCapabilities()) {
                List<String> futureOwners = new ArrayList<>();
                for (int futureIndex = index + 1; futureIndex < units.size(); futureIndex++) {
                    CapabilityPartitionUnit futureUnit = units.get(futureIndex);
                    if (futureUnit.ownedCapabilities().contains(capability)) {
                        futureOwners.add(renderUnitId(futureUnit.id()));
                    }
                }
                if (futureOwners.size() != 1) {
                    issues.add("子任务 " + renderUnitId(unit.id()) + " 标记为 deferred 的能力 " + capability + " 必须由后续唯一子任务接手。");
                }
            }
        }
        issues.addAll(validateSharedFileDeferredBoundary(units));
        if (issues.isEmpty()) {
            return CoverageResult.success();
        }
        return CoverageResult.failure("当前实现计划的 capability partition 未通过结构校验。", issues);
    }

    private List<String> safeCapabilities(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String candidate = blank(value);
            if (!candidate.isBlank()) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> safePathsFromChanges(List<FileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (FileChange change : changes) {
            if (change == null) {
                continue;
            }
            String candidate = normalizePath(change.path());
            if (!candidate.isBlank()) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> safePaths(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String candidate = normalizePath(value);
            if (!candidate.isBlank()) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> validateSharedFileDeferredBoundary(List<CapabilityPartitionUnit> units) {
        List<String> issues = new ArrayList<>();
        for (int index = 0; index < units.size(); index++) {
            CapabilityPartitionUnit unit = units.get(index);
            LinkedHashSet<String> sharedPaths = new LinkedHashSet<>();
            LinkedHashSet<String> futureOwnedCapabilities = new LinkedHashSet<>();
            for (int futureIndex = index + 1; futureIndex < units.size(); futureIndex++) {
                CapabilityPartitionUnit futureUnit = units.get(futureIndex);
                LinkedHashSet<String> overlap = new LinkedHashSet<>(unit.scopedPaths());
                overlap.retainAll(futureUnit.scopedPaths());
                if (overlap.isEmpty()) {
                    continue;
                }
                sharedPaths.addAll(overlap);
                futureOwnedCapabilities.addAll(futureUnit.ownedCapabilities());
            }
            if (sharedPaths.isEmpty() || futureOwnedCapabilities.isEmpty()) {
                continue;
            }
            if (unit.deferredCapabilities().isEmpty()) {
                issues.add("子任务 " + renderUnitId(unit.id())
                        + " 与后续子任务共享文件 " + String.join("、", sharedPaths)
                        + "，必须显式声明 deferredCapabilities 来锁定当前与下游 capability boundary。");
                continue;
            }
            LinkedHashSet<String> anchoredDeferredCapabilities = new LinkedHashSet<>(unit.deferredCapabilities());
            anchoredDeferredCapabilities.retainAll(futureOwnedCapabilities);
            if (anchoredDeferredCapabilities.isEmpty()) {
                issues.add("子任务 " + renderUnitId(unit.id())
                        + " 虽然声明了 deferredCapabilities，但没有覆盖共享文件 "
                        + String.join("、", sharedPaths)
                        + " 对应的下游能力：" + String.join("、", futureOwnedCapabilities));
            }
        }
        return issues;
    }

    private String normalizePath(String value) {
        String candidate = blank(value);
        if (candidate.isBlank()) {
            return "";
        }
        return java.nio.file.Path.of(candidate).normalize().toString().replace('\\', '/');
    }

    private String renderUnitId(String value) {
        return blank(value).isBlank() ? "<unknown>" : blank(value);
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private record CapabilityPartitionUnit(
            String id,
            List<String> ownedCapabilities,
            List<String> deferredCapabilities,
            List<String> scopedPaths
    ) {
    }
}
