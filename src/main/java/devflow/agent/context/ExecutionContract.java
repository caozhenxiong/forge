package devflow.agent.context;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record ExecutionContract(
        boolean entryRequired,
        String entryKind,
        String entryPackagingMode,
        String runtimeOwnershipMode,
        boolean launchRequired,
        boolean surfaceRequired,
        List<String> acceptanceSignals
) {

    public ExecutionContract {
        entryKind = normalizeEntryKindValue(entryKind);
        entryPackagingMode = normalizePackagingModeValue(entryPackagingMode);
        runtimeOwnershipMode = normalizeRuntimeOwnershipModeValue(runtimeOwnershipMode);
        acceptanceSignals = normalizeAcceptanceSignals(acceptanceSignals);
    }

    public ExecutionContract(
            boolean entryRequired,
            String entryKind,
            boolean launchRequired,
            boolean surfaceRequired,
            List<String> acceptanceSignals
    ) {
        this(entryRequired, entryKind, EntryPackagingMode.NOT_APPLICABLE.wireValue(),
                ContractRuntimeOwnershipMode.NOT_APPLICABLE.wireValue(), launchRequired, surfaceRequired, acceptanceSignals);
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
                - entryPackagingMode: %s
                - runtimeOwnershipMode: %s
                - launchRequired: %s
                - surfaceRequired: %s

                ### %s
                %s
                """.formatted(
                language.choose("执行契约（绑定）", "Execution Contract (Binding)"),
                language.choose("这一节定义后续实现、review 与测试必须满足的最小可运行/可启动约束。", "This section defines the minimum runnable / launchable constraints that downstream implementation, review, and testing must satisfy."),
                entryRequired,
                blank(entryKind, language),
                blank(entryPackagingMode, language),
                blank(runtimeOwnershipMode, language),
                launchRequired,
                surfaceRequired,
                language.choose("验收信号", "Acceptance Signals"),
                bullets(acceptanceSignals, language)
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

    public String normalizedEntryPackagingMode() {
        return entryPackagingMode == null ? "" : entryPackagingMode.trim().toLowerCase(Locale.ROOT);
    }

    public EntryPackagingMode normalizedEntryPackagingModeEnum() {
        return EntryPackagingMode.fromWireValue(normalizedEntryPackagingMode());
    }

    public String normalizedRuntimeOwnershipMode() {
        return runtimeOwnershipMode == null ? "" : runtimeOwnershipMode.trim().toLowerCase(Locale.ROOT);
    }

    public ContractRuntimeOwnershipMode normalizedRuntimeOwnershipModeEnum() {
        return ContractRuntimeOwnershipMode.fromWireValue(normalizedRuntimeOwnershipMode());
    }

    public ExecutionContract normalized() {
        ExecutionEntryKind normalizedEntryKind = normalizedEntryKindEnum();
        EntryPackagingMode normalizedPackagingMode = normalizeEntryPackagingMode(normalizedEntryKind, normalizedEntryPackagingModeEnum());
        ContractRuntimeOwnershipMode normalizedOwnershipMode = normalizeRuntimeOwnershipMode(
                normalizedEntryKind,
                normalizedPackagingMode,
                normalizedRuntimeOwnershipModeEnum()
        );
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
                normalizedPackagingMode.wireValue(),
                normalizedOwnershipMode.wireValue(),
                normalizedLaunchRequired,
                normalizedSurfaceRequired,
                normalizedSignals
        );
    }

    /**
     * authority corpus 只允许投影稳定的 binding contract facts。
     *
     * <p>runtime ownership 仍保留在结构化 execution contract 中，
     * 但不能回灌到 authority prose，避免把一次实现期判断循环强化成文档硬约束。
     */
    public List<String> bindingAuthorityFacts() {
        List<String> facts = new ArrayList<>();
        if (entryRequired) {
            facts.add("entry required");
        }
        if (launchRequired) {
            facts.add("launch required");
        }
        if (surfaceRequired) {
            facts.add("surface required");
        }
        if (entryKind != null && !entryKind.isBlank()) {
            facts.add(entryKind.trim());
        }
        if (entryPackagingMode != null && !entryPackagingMode.isBlank()) {
            facts.add(entryPackagingMode.trim());
        }
        facts.addAll(acceptanceSignals);
        return List.copyOf(facts);
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

    private static String normalizePackagingModeValue(String value) {
        return EntryPackagingMode.fromWireValue(value).wireValue();
    }

    private static String normalizeRuntimeOwnershipModeValue(String value) {
        return ContractRuntimeOwnershipMode.fromWireValue(value).wireValue();
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

    private static EntryPackagingMode normalizeEntryPackagingMode(
            ExecutionEntryKind entryKind,
            EntryPackagingMode packagingMode
    ) {
        if (packagingMode != null && packagingMode != EntryPackagingMode.NOT_APPLICABLE) {
            return packagingMode;
        }
        if (entryKind == ExecutionEntryKind.HTML_ENTRY) {
            return EntryPackagingMode.ENTRY_WITH_LOCAL_DEPENDENCIES;
        }
        return EntryPackagingMode.NOT_APPLICABLE;
    }

    private static ContractRuntimeOwnershipMode normalizeRuntimeOwnershipMode(
            ExecutionEntryKind entryKind,
            EntryPackagingMode packagingMode,
            ContractRuntimeOwnershipMode runtimeOwnershipMode
    ) {
        if (runtimeOwnershipMode != null && runtimeOwnershipMode != ContractRuntimeOwnershipMode.NOT_APPLICABLE) {
            return runtimeOwnershipMode;
        }
        if (entryKind == ExecutionEntryKind.HTML_ENTRY && packagingMode == EntryPackagingMode.SELF_CONTAINED_ENTRY) {
            return ContractRuntimeOwnershipMode.ENTRY_OWNED;
        }
        return ContractRuntimeOwnershipMode.NOT_APPLICABLE;
    }
}
