package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record ExecutionContract(
        boolean entryRequired,
        String entryKind,
        boolean launchRequired,
        boolean surfaceRequired,
        List<String> acceptanceSignals
) {

    public ExecutionContract {
        entryKind = normalizeEntryKindValue(entryKind);
        acceptanceSignals = normalizeAcceptanceSignals(acceptanceSignals);
    }

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                ## %s

                > %s

                - entryRequired: %s
                - entryKind: %s
                - launchRequired: %s
                - surfaceRequired: %s

                ### %s
                %s
                """.formatted(
                language.choose("执行契约（绑定）", "Execution Contract (Binding)"),
                language.choose("这一节定义后续实现、review 与测试必须满足的最小可运行/可启动约束。", "This section defines the minimum runnable / launchable constraints that downstream implementation, review, and testing must satisfy."),
                entryRequired,
                blank(entryKind, language),
                launchRequired,
                surfaceRequired,
                language.choose("验收信号", "Acceptance Signals"),
                bullets(acceptanceSignals, language)
        ).trim();
    }

    public String toMetadataSectionMarkdown(int sectionNumber) {
        return """
                ## %d. Contract Metadata

                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                - %s: %s
                """.formatted(
                sectionNumber,
                ContractMetadataKeys.RUNTIME_ENTRY_REQUIRED,
                entryRequired,
                ContractMetadataKeys.RUNTIME_ENTRY_KIND,
                blank(entryKind, DocumentLanguage.EN),
                ContractMetadataKeys.RUNTIME_LAUNCH_REQUIRED,
                launchRequired,
                ContractMetadataKeys.RUNTIME_SURFACE_REQUIRED,
                surfaceRequired,
                ContractMetadataKeys.RUNTIME_ACCEPTANCE_SIGNALS,
                acceptanceSignals == null || acceptanceSignals.isEmpty()
                        ? PlaceholderValues.machineNone()
                        : String.join(", ", acceptanceSignals)
        ).trim();
    }

    public boolean requiresHtmlEntry() {
        return entryRequired && normalizedEntryKindEnum() == ExecutionEntryKind.HTML_ENTRY;
    }

    public String normalizedEntryKind() {
        return entryKind == null ? "" : entryKind.trim().toLowerCase();
    }

    public ExecutionEntryKind normalizedEntryKindEnum() {
        return ExecutionEntryKind.fromWireValue(normalizedEntryKind());
    }

    public ExecutionContract normalized() {
        ExecutionEntryKind normalizedEntryKind = normalizedEntryKindEnum();
        boolean normalizedEntryRequired = entryRequired || normalizedEntryKind != ExecutionEntryKind.UNSPECIFIED;
        boolean normalizedLaunchRequired = launchRequired
                || normalizedEntryKind.impliesLaunchableEntry()
                || acceptanceSignalsContain("page-opens")
                || acceptanceSignalsContain("runtime-starts")
                || acceptanceSignalsContain("process-starts")
                || acceptanceSignalsContain("service-starts");
        boolean normalizedSurfaceRequired = surfaceRequired
                || normalizedEntryKind.impliesInteractiveSurface()
                || acceptanceSignalsContain("runtime-surface-renders")
                || acceptanceSignalsContain("play-surface-renders")
                || acceptanceSignalsContain("ui-renders");
        List<String> normalizedSignals = appendDefaultAcceptanceSignals(
                acceptanceSignals,
                normalizedEntryKind,
                normalizedLaunchRequired,
                normalizedSurfaceRequired
        );
        return new ExecutionContract(
                normalizedEntryRequired,
                normalizedEntryKind.wireValue(),
                normalizedLaunchRequired,
                normalizedSurfaceRequired,
                normalizedSignals
        );
    }

    private String bullets(List<String> items, DocumentLanguage language) {
        if (items == null || items.isEmpty()) {
            return PlaceholderValues.bulletNone(language);
        }
        return items.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .map(item -> "- " + item)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(PlaceholderValues.bulletNone(language));
    }

    private String blank(String value, DocumentLanguage language) {
        return PlaceholderValues.orNone(value, language);
    }

    private boolean acceptanceSignalsContain(String signal) {
        if (signal == null || signal.isBlank() || acceptanceSignals == null || acceptanceSignals.isEmpty()) {
            return false;
        }
        return acceptanceSignals.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .anyMatch(signal.trim().toLowerCase(Locale.ROOT)::equals);
    }

    private static String normalizeEntryKindValue(String value) {
        if (value == null || value.isBlank()) {
            return "unspecified";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> normalizeAcceptanceSignals(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static List<String> appendDefaultAcceptanceSignals(
            List<String> acceptanceSignals,
            ExecutionEntryKind normalizedEntryKind,
            boolean launchRequired,
            boolean surfaceRequired
    ) {
        List<String> values = new ArrayList<>(normalizeAcceptanceSignals(acceptanceSignals));
        if (launchRequired) {
            addSignal(values, normalizedEntryKind.defaultLaunchAcceptanceSignal());
        }
        if (surfaceRequired && normalizedEntryKind == ExecutionEntryKind.HTML_ENTRY) {
            addSignal(values, "runtime-surface-renders");
        }
        return List.copyOf(values);
    }

    private static void addSignal(List<String> values, String signal) {
        if (signal == null || signal.isBlank()) {
            return;
        }
        String normalized = signal.trim().toLowerCase(Locale.ROOT);
        if (!values.contains(normalized)) {
            values.add(normalized);
        }
    }
}
