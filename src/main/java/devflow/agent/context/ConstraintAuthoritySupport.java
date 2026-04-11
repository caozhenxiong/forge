package devflow.agent.context;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一拼装可直接复用的 authority corpus。
 *
 * <p>这里故意只保留“结构化来源汇总”这一层能力，不再承担：
 * 1. 从正文自然语言里猜语义；
 * 2. 从自由文本里反推 optional / recommendation / design choice；
 * 3. 重写正文表达或对 prose 做二次裁决。
 */
public final class ConstraintAuthoritySupport {

    private ConstraintAuthoritySupport() {
    }

    public static String buildAuthorityCorpus(
            String goal,
            String constraints,
            ConstraintSourceMetadata metadata,
            ExecutionContract executionContract
    ) {
        List<String> parts = new ArrayList<>();
        if (goal != null && !goal.isBlank()) {
            parts.add(goal.trim());
        }
        if (constraints != null && !constraints.isBlank()) {
            parts.add(constraints.trim());
        }
        if (metadata != null) {
            addAll(parts, metadata.hardUserRequirements());
            addAll(parts, metadata.hardUpstreamFacts());
        }
        if (executionContract != null) {
            if (executionContract.entryRequired()) {
                parts.add("entry required");
            }
            if (executionContract.launchRequired()) {
                parts.add("launch required");
            }
            if (executionContract.surfaceRequired()) {
                parts.add("surface required");
            }
            if (executionContract.entryKind() != null && !executionContract.entryKind().isBlank()) {
                parts.add(executionContract.entryKind().trim());
            }
            if (executionContract.entryPackagingMode() != null && !executionContract.entryPackagingMode().isBlank()) {
                parts.add(executionContract.entryPackagingMode().trim());
            }
            if (executionContract.runtimeOwnershipMode() != null && !executionContract.runtimeOwnershipMode().isBlank()) {
                parts.add(executionContract.runtimeOwnershipMode().trim());
            }
            addAll(parts, executionContract.acceptanceSignals());
        }
        return String.join("\n", parts);
    }

    private static void addAll(List<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(target::add);
    }
}
